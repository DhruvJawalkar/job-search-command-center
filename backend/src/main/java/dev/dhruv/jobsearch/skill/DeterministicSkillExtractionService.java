package dev.dhruv.jobsearch.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;

@Service
public class DeterministicSkillExtractionService {

    private static final Pattern SEGMENT_BOUNDARY = Pattern.compile("[\\r\\n]+|(?<=[.!?;])\\s+");

    private final CanonicalSkillRepository skills;
    private final SkillAliasRepository aliases;
    private final JobDescriptionSnapshotRepository snapshots;
    private final JobSkillObservationRepository observations;
    private final JobOpportunityRepository opportunities;

    public DeterministicSkillExtractionService(CanonicalSkillRepository skills, SkillAliasRepository aliases,
            JobDescriptionSnapshotRepository snapshots, JobSkillObservationRepository observations,
            JobOpportunityRepository opportunities) {
        this.skills = skills;
        this.aliases = aliases;
        this.snapshots = snapshots;
        this.observations = observations;
        this.opportunities = opportunities;
    }

    @Transactional
    public ExtractionResult extract(ExtractionCommand command) {
        Set<UUID> selected = command.opportunityIds() == null ? Set.of() : Set.copyOf(command.opportunityIds());
        List<SnapshotSource> source = opportunities.findByDemoFalseOrderByDiscoveredAtDesc().stream()
                .filter(opportunity -> selected.isEmpty() || selected.contains(opportunity.getId()))
                .filter(opportunity -> command.includeArchived() || isActive(opportunity.getStatus()))
                .map(opportunity -> new SnapshotSource(opportunity, preferredSnapshot(opportunity)))
                .filter(item -> item.snapshot() != null)
                .toList();

        List<CanonicalSkill> catalog = skills.findByActiveTrueOrderByNameAsc();
        Map<UUID, List<String>> terms = termsBySkill(catalog);
        int observationsCreated = 0;
        int matchesFound = 0;
        List<OpportunityExtraction> details = new ArrayList<>();

        for (SnapshotSource item : source) {
            JobOpportunity opportunity = item.opportunity();
            JobDescriptionSnapshot snapshot = item.snapshot();
            String content = snapshot.getContent();

            Map<UUID, Match> matches = findMatches(content, catalog, terms);
            matchesFound += matches.size();
            int createdForOpportunity = 0;
            for (Map.Entry<UUID, Match> entry : matches.entrySet()) {
                Match match = entry.getValue();
                String fingerprint = sha256(normalizeEvidence(match.evidence()));
                if (observations.findBySnapshotIdAndSkillIdAndEvidenceFingerprint(
                        snapshot.getId(), entry.getKey(), fingerprint).isPresent()) continue;
                CanonicalSkill skill = catalog.stream().filter(candidate -> candidate.getId().equals(entry.getKey())).findFirst()
                        .orElseThrow();
                observations.save(new JobSkillObservation(snapshot, skill, match.strength(), match.evidence(),
                        fingerprint, SkillExtractionMethod.DETERMINISTIC));
                observationsCreated++;
                createdForOpportunity++;
            }
            details.add(new OpportunityExtraction(opportunity.getId(), opportunity.getCompanyName(),
                    opportunity.getRoleTitle(), matches.size(), createdForOpportunity));
        }
        return new ExtractionResult(source.size(), catalog.size(), 0, matchesFound,
                observationsCreated, details);
    }

    private JobDescriptionSnapshot preferredSnapshot(JobOpportunity opportunity) {
        var live = snapshots.findFirstByOpportunityIdAndSourceTypeOrderByCapturedAtDesc(
                opportunity.getId(), SnapshotSourceType.LIVE_JOB_PAGE);
        if (live.isPresent()) return live.get();
        return snapshots.findByOpportunityIdOrderByCapturedAtDesc(opportunity.getId()).stream()
                .filter(snapshot -> isFullDescriptionSource(snapshot.getSourceType()))
                .findFirst().orElse(null);
    }

    static boolean isFullDescriptionSource(SnapshotSourceType sourceType) {
        return sourceType == SnapshotSourceType.LIVE_JOB_PAGE
                || sourceType == SnapshotSourceType.PASTED_DESCRIPTION
                || sourceType == SnapshotSourceType.MANUAL_DESCRIPTION;
    }

    private Map<UUID, List<String>> termsBySkill(List<CanonicalSkill> catalog) {
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        Map<UUID, List<String>> aliasMap = new HashMap<>();
        for (SkillAlias alias : aliases.findAllByOrderByAliasAsc()) {
            aliasMap.computeIfAbsent(alias.getSkill().getId(), ignored -> new ArrayList<>()).add(alias.getAlias());
        }
        for (CanonicalSkill skill : catalog) {
            List<String> values = new ArrayList<>();
            values.add(skill.getName());
            values.addAll(aliasMap.getOrDefault(skill.getId(), List.of()));
            result.put(skill.getId(), values.stream().distinct()
                    .sorted(Comparator.comparingInt(String::length).reversed()).toList());
        }
        return result;
    }

    private Map<UUID, Match> findMatches(String content, List<CanonicalSkill> catalog,
            Map<UUID, List<String>> terms) {
        Map<UUID, Match> matches = new LinkedHashMap<>();
        SkillStrength section = SkillStrength.MENTIONED;
        for (String rawSegment : SEGMENT_BOUNDARY.split(content)) {
            String segment = rawSegment.trim();
            if (segment.isBlank()) continue;
            String lower = segment.toLowerCase(Locale.ROOT);
            section = sectionFor(lower, section);
            SkillStrength strength = strengthFor(lower, section);
            for (CanonicalSkill skill : catalog) {
                if (!containsAny(segment, terms.getOrDefault(skill.getId(), List.of()))) continue;
                Match candidate = new Match(strength, excerpt(segment));
                Match current = matches.get(skill.getId());
                if (current == null || priority(candidate.strength()) > priority(current.strength())) {
                    matches.put(skill.getId(), candidate);
                }
            }
        }
        return matches;
    }

    private boolean containsAny(String segment, List<String> terms) {
        for (String term : terms) {
            Pattern pattern = Pattern.compile("(?i)(?<![\\p{Alnum}+#.])" + Pattern.quote(term)
                    + "(?![\\p{Alnum}+#.])");
            if (pattern.matcher(segment).find()) return true;
        }
        return false;
    }

    private SkillStrength sectionFor(String text, SkillStrength current) {
        if (text.matches(".*\\b(preferred qualifications?|nice to haves?|bonus|desired qualifications?)\\b.*")) {
            return SkillStrength.PREFERRED;
        }
        if (text.matches(".*\\b(minimum qualifications?|required qualifications?|requirements?)\\b.*")) {
            return SkillStrength.REQUIRED;
        }
        if (text.matches(".*\\b(responsibilities|what you will do|about the role|description)\\b.*")) {
            return SkillStrength.MENTIONED;
        }
        return current;
    }

    private SkillStrength strengthFor(String text, SkillStrength section) {
        if (text.matches(".*\\b(preferred|nice to have|bonus|ideally|familiarity with|knowledge of|a plus)\\b.*")) {
            return SkillStrength.PREFERRED;
        }
        if (text.matches(".*\\b(required|must|minimum qualification|deep expertise|strong expertise|proficiency in|hands-on experience)\\b.*")) {
            return SkillStrength.REQUIRED;
        }
        return section;
    }

    private int priority(SkillStrength strength) {
        return switch (strength) {
            case REQUIRED -> 3;
            case PREFERRED -> 2;
            case MENTIONED -> 1;
        };
    }

    private boolean isActive(OpportunityStatus status) {
        return status != OpportunityStatus.ARCHIVED && status != OpportunityStatus.SKIPPED
                && status != OpportunityStatus.EXPIRED;
    }

    private String excerpt(String segment) {
        String normalized = segment.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 597) + "...";
    }

    private String normalizeEvidence(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record Match(SkillStrength strength, String evidence) {}
    private record SnapshotSource(JobOpportunity opportunity, JobDescriptionSnapshot snapshot) {}

    public record ExtractionCommand(List<UUID> opportunityIds, boolean includeArchived) {}
    public record OpportunityExtraction(UUID opportunityId, String companyName, String roleTitle,
            int matchesFound, int observationsCreated) {}
    public record ExtractionResult(int opportunitiesScanned, int catalogSize, int snapshotsCreated,
            int matchesFound, int observationsCreated, List<OpportunityExtraction> opportunities) {}
}
