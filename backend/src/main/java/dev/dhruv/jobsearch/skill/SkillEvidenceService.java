package dev.dhruv.jobsearch.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class SkillEvidenceService {

    private final CanonicalSkillRepository skills;
    private final SkillAliasRepository aliases;
    private final JobDescriptionSnapshotRepository snapshots;
    private final JobSkillObservationRepository observations;
    private final JobOpportunityRepository opportunities;
    private final PersonalSkillBacklogService personalBacklog;

    public SkillEvidenceService(CanonicalSkillRepository skills, SkillAliasRepository aliases,
            JobDescriptionSnapshotRepository snapshots, JobSkillObservationRepository observations,
            JobOpportunityRepository opportunities, PersonalSkillBacklogService personalBacklog) {
        this.skills = skills;
        this.aliases = aliases;
        this.snapshots = snapshots;
        this.observations = observations;
        this.opportunities = opportunities;
        this.personalBacklog = personalBacklog;
    }

    @Transactional
    public SkillView createSkill(NewSkill command) {
        String name = required(command.name(), "Skill name");
        String normalizedName = normalize(name);
        ensureTermAvailable(normalizedName);
        CanonicalSkill skill = skills.save(new CanonicalSkill(name, normalizedName, command.category(), command.description()));
        List<String> requestedAliases = command.aliases() == null ? List.of() : command.aliases();
        Map<String, String> uniqueAliases = new LinkedHashMap<>();
        for (String alias : requestedAliases) {
            String value = required(alias, "Skill alias");
            String normalized = normalize(value);
            if (!normalized.equals(normalizedName)) uniqueAliases.putIfAbsent(normalized, value);
        }
        for (Map.Entry<String, String> alias : uniqueAliases.entrySet()) {
            ensureTermAvailable(alias.getKey());
            aliases.save(new SkillAlias(skill, alias.getValue(), alias.getKey()));
        }
        return SkillView.from(skill, uniqueAliases.values().stream().sorted().toList());
    }

    @Transactional
    public SeedResult seedReviewedBacklog() {
        int skillsCreated = 0;
        int aliasesCreated = 0;
        for (SkillSeedCatalog.SeedSkill seed : SkillSeedCatalog.reviewedBacklog()) {
            String normalizedName = normalize(seed.name());
            CanonicalSkill skill = skills.findByNormalizedName(normalizedName).orElse(null);
            if (skill == null) {
                if (aliases.findByNormalizedAlias(normalizedName).isPresent()) continue;
                skill = skills.save(new CanonicalSkill(seed.name(), normalizedName, seed.category(), seed.description()));
                skillsCreated++;
            }
            for (String alias : seed.aliases()) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.equals(skill.getNormalizedName())) continue;
                if (skills.findByNormalizedName(normalizedAlias).isPresent()) continue;
                var existing = aliases.findByNormalizedAlias(normalizedAlias);
                if (existing.isPresent()) continue;
                aliases.save(new SkillAlias(skill, alias, normalizedAlias));
                aliasesCreated++;
            }
        }
        return new SeedResult(skillsCreated, aliasesCreated, skills.count());
    }

    @Transactional
    public SkillView addAlias(UUID skillId, String alias) {
        CanonicalSkill skill = getSkill(skillId);
        String value = required(alias, "Skill alias");
        String normalized = normalize(value);
        if (!normalized.equals(skill.getNormalizedName())) {
            ensureTermAvailable(normalized);
            aliases.save(new SkillAlias(skill, value, normalized));
        }
        return skillView(skill, aliases.findAllByOrderByAliasAsc());
    }

    @Transactional
    public SnapshotView captureSnapshot(NewSnapshot command) {
        var opportunity = opportunities.findById(command.opportunityId())
                .orElseThrow(() -> new NotFoundException("Opportunity " + command.opportunityId() + " was not found."));
        String content = required(command.content(), "Snapshot content");
        String hash = sha256(content);
        JobDescriptionSnapshot snapshot = snapshots.findByOpportunityIdAndContentHash(opportunity.getId(), hash)
                .orElseGet(() -> snapshots.save(new JobDescriptionSnapshot(opportunity, command.sourceType(),
                        command.sourceLabel(), content, hash)));
        return SnapshotView.from(snapshot);
    }

    @Transactional
    public ObservationView createObservation(NewObservation command) {
        JobDescriptionSnapshot snapshot = snapshots.findById(command.snapshotId())
                .orElseThrow(() -> new NotFoundException("Job-description snapshot " + command.snapshotId() + " was not found."));
        CanonicalSkill skill = getSkill(command.skillId());
        String evidence = required(command.evidenceSnippet(), "Evidence snippet");
        String fingerprint = sha256(normalizeEvidence(evidence));
        JobSkillObservation observation = observations
                .findBySnapshotIdAndSkillIdAndEvidenceFingerprint(snapshot.getId(), skill.getId(), fingerprint)
                .orElseGet(() -> observations.save(new JobSkillObservation(snapshot, skill, command.strength(), evidence,
                        fingerprint, command.extractionMethod())));
        return ObservationView.from(observation);
    }

    @Transactional
    public AssistedPublishResult publishAssistedEvidence(UUID opportunityId, String content, List<AssistedSkill> suggested) {
        if (suggested == null || suggested.isEmpty()) {
            throw new IllegalArgumentException("The assistance run contains no skill suggestions.");
        }
        SnapshotView snapshot = captureSnapshot(new NewSnapshot(opportunityId, SnapshotSourceType.PASTED_DESCRIPTION,
                "AI-assisted inbox structuring", content));
        List<ObservationView> published = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (AssistedSkill suggestion : suggested) {
            String name = required(suggestion.name(), "Suggested skill name");
            String normalized = normalize(name);
            CanonicalSkill skill = skills.findByNormalizedName(normalized)
                    .orElseGet(() -> aliases.findByNormalizedAlias(normalized).map(SkillAlias::getSkill).orElse(null));
            if (skill == null) {
                unmatched.add(name);
                continue;
            }
            published.add(createObservation(new NewObservation(snapshot.id(), skill.getId(),
                    suggestion.strength() == null ? SkillStrength.MENTIONED : suggestion.strength(),
                    required(suggestion.evidenceSnippet(), "Skill evidence"), SkillExtractionMethod.AI_ASSISTED)));
        }
        return new AssistedPublishResult(published, unmatched);
    }

    @Transactional
    public ObservationView reviewObservation(UUID observationId, SkillReviewStatus status, String note) {
        JobSkillObservation observation = observations.findById(observationId)
                .orElseThrow(() -> new NotFoundException("Skill observation " + observationId + " was not found."));
        observation.review(status, note);
        return ObservationView.from(observation);
    }

    @Transactional
    public List<ObservationView> reviewObservations(List<UUID> observationIds, SkillReviewStatus status, String note) {
        if (observationIds == null || observationIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one skill observation.");
        }
        return observationIds.stream().distinct().map(id -> {
            JobSkillObservation observation = observations.findById(id)
                    .orElseThrow(() -> new NotFoundException("Skill observation " + id + " was not found."));
            observation.review(status, note);
            return ObservationView.from(observation);
        }).toList();
    }

    @Transactional
    public ObservationView correctObservation(UUID observationId, Correction command) {
        JobSkillObservation observation = observations.findById(observationId)
                .orElseThrow(() -> new NotFoundException("Skill observation " + observationId + " was not found."));
        CanonicalSkill skill = getSkill(command.skillId());
        String evidence = required(command.evidenceSnippet(), "Evidence snippet");
        String fingerprint = sha256(normalizeEvidence(evidence));
        var duplicate = observations.findBySnapshotIdAndSkillIdAndEvidenceFingerprint(
                observation.getSnapshot().getId(), skill.getId(), fingerprint);
        if (duplicate.isPresent() && !duplicate.get().getId().equals(observationId)) {
            JobSkillObservation existing = duplicate.get();
            existing.review(SkillReviewStatus.ACCEPTED, command.note());
            observations.delete(observation);
            return ObservationView.from(existing);
        }
        observation.correct(skill, command.strength(), evidence, fingerprint, command.note());
        return ObservationView.from(observation);
    }

    @Transactional
    public SkillView mergeSkill(UUID sourceSkillId, UUID targetSkillId) {
        if (sourceSkillId.equals(targetSkillId)) {
            throw new IllegalArgumentException("Choose two different canonical skills to merge.");
        }
        CanonicalSkill source = getSkill(sourceSkillId);
        CanonicalSkill target = getSkill(targetSkillId);

        for (JobSkillObservation observation : observations.findBySkillId(sourceSkillId)) {
            var duplicate = observations.findBySnapshotIdAndSkillIdAndEvidenceFingerprint(
                    observation.getSnapshot().getId(), targetSkillId, observation.getEvidenceFingerprint());
            if (duplicate.isPresent()) {
                JobSkillObservation existing = duplicate.get();
                if (observation.getReviewStatus() == SkillReviewStatus.ACCEPTED
                        && existing.getReviewStatus() != SkillReviewStatus.ACCEPTED) {
                    existing.review(SkillReviewStatus.ACCEPTED, "Accepted evidence preserved while merging "
                            + source.getName() + " into " + target.getName() + ".");
                }
                observations.delete(observation);
            } else {
                observation.reassignTo(target);
            }
        }

        for (SkillAlias alias : aliases.findBySkillId(sourceSkillId)) {
            var existing = aliases.findByNormalizedAlias(alias.getNormalizedAlias());
            if (alias.getNormalizedAlias().equals(target.getNormalizedName())
                    || (existing.isPresent() && !existing.get().getId().equals(alias.getId()))) {
                aliases.delete(alias);
            } else {
                alias.reassignTo(target);
            }
        }

        String sourceName = source.getName();
        String normalizedSourceName = source.getNormalizedName();
        personalBacklog.mergeSkillBacklog(source, target);
        skills.delete(source);
        skills.flush();
        if (!normalizedSourceName.equals(target.getNormalizedName())
                && aliases.findByNormalizedAlias(normalizedSourceName).isEmpty()) {
            aliases.save(new SkillAlias(target, sourceName, normalizedSourceName));
        }
        return skillView(target, aliases.findAllByOrderByAliasAsc());
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<SkillAlias> allAliases = aliases.findAllByOrderByAliasAsc();
        List<SkillView> catalog = skills.findByActiveTrueOrderByNameAsc().stream()
                .map(skill -> skillView(skill, allAliases)).toList();
        List<JobSkillObservation> storedEvidence = observations.findAllByOrderByUpdatedAtDesc();
        List<ObservationView> evidence = storedEvidence.stream().map(ObservationView::from).toList();
        long proposed = evidence.stream().filter(item -> item.reviewStatus() == SkillReviewStatus.PROPOSED).count();
        long accepted = storedEvidence.stream()
                .filter(item -> item.getReviewStatus() == SkillReviewStatus.ACCEPTED)
                .filter(item -> DeterministicSkillExtractionService.isFullDescriptionSource(
                        item.getSnapshot().getSourceType()))
                .count();
        long rejected = evidence.stream().filter(item -> item.reviewStatus() == SkillReviewStatus.REJECTED).count();
        return new Overview(catalog.size(), proposed, accepted, rejected, catalog, evidence);
    }

    @Transactional(readOnly = true)
    public List<ObservationView> listObservations(UUID opportunityId, SkillReviewStatus reviewStatus) {
        return observations.findAllByOrderByUpdatedAtDesc().stream()
                .filter(item -> opportunityId == null || item.getOpportunity().getId().equals(opportunityId))
                .filter(item -> reviewStatus == null || item.getReviewStatus() == reviewStatus)
                .map(ObservationView::from).toList();
    }

    private CanonicalSkill getSkill(UUID skillId) {
        return skills.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Canonical skill " + skillId + " was not found."));
    }

    private SkillView skillView(CanonicalSkill skill, List<SkillAlias> allAliases) {
        List<String> names = new ArrayList<>();
        allAliases.stream().filter(alias -> alias.getSkill().getId().equals(skill.getId()))
                .map(SkillAlias::getAlias).forEach(names::add);
        return SkillView.from(skill, names);
    }

    private void ensureTermAvailable(String normalized) {
        if (skills.findByNormalizedName(normalized).isPresent() || aliases.findByNormalizedAlias(normalized).isPresent()) {
            throw new IllegalStateException("That skill name or alias already belongs to the taxonomy.");
        }
    }

    static String normalize(String value) {
        return required(value, "Skill term").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String normalizeEvidence(String value) {
        return required(value, "Evidence snippet").replaceAll("\\s+", " ").trim();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record NewSkill(String name, SkillCategory category, String description, List<String> aliases) {}
    public record NewSnapshot(UUID opportunityId, SnapshotSourceType sourceType, String sourceLabel, String content) {}
    public record NewObservation(UUID snapshotId, UUID skillId, SkillStrength strength, String evidenceSnippet,
            SkillExtractionMethod extractionMethod) {}
    public record Correction(UUID skillId, SkillStrength strength, String evidenceSnippet, String note) {}
    public record AssistedSkill(String name, SkillStrength strength, String evidenceSnippet) {}
    public record AssistedPublishResult(List<ObservationView> published, List<String> unmatched) {}
    public record SeedResult(int skillsCreated, int aliasesCreated, long catalogSize) {}

    public record Overview(long catalogSize, long proposedCount, long acceptedCount, long rejectedCount,
            List<SkillView> skills, List<ObservationView> observations) {}

    public record SkillView(UUID id, String name, String normalizedName, SkillCategory category, String description,
            boolean active, List<String> aliases, Instant createdAt, Instant updatedAt) {
        static SkillView from(CanonicalSkill skill, List<String> aliases) {
            return new SkillView(skill.getId(), skill.getName(), skill.getNormalizedName(), skill.getCategory(),
                    skill.getDescription(), skill.isActive(), aliases, skill.getCreatedAt(), skill.getUpdatedAt());
        }
    }

    public record SnapshotView(UUID id, UUID opportunityId, String companyName, String roleTitle,
            SnapshotSourceType sourceType, String sourceLabel, String contentHash, Instant capturedAt) {
        static SnapshotView from(JobDescriptionSnapshot snapshot) {
            return new SnapshotView(snapshot.getId(), snapshot.getOpportunity().getId(),
                    snapshot.getOpportunity().getCompanyName(), snapshot.getOpportunity().getRoleTitle(),
                    snapshot.getSourceType(), snapshot.getSourceLabel(), snapshot.getContentHash(), snapshot.getCapturedAt());
        }
    }

    public record ObservationView(UUID id, UUID opportunityId, String companyName, String roleTitle, UUID snapshotId,
            UUID skillId, String skillName, SkillCategory category, SkillStrength strength, String evidenceSnippet,
            SkillExtractionMethod extractionMethod, SkillReviewStatus reviewStatus, String reviewNote,
            Instant observedAt, Instant reviewedAt, Instant updatedAt) {
        static ObservationView from(JobSkillObservation observation) {
            return new ObservationView(observation.getId(), observation.getOpportunity().getId(),
                    observation.getOpportunity().getCompanyName(), observation.getOpportunity().getRoleTitle(),
                    observation.getSnapshot().getId(), observation.getSkill().getId(), observation.getSkill().getName(),
                    observation.getSkill().getCategory(), observation.getStrength(), observation.getEvidenceSnippet(),
                    observation.getExtractionMethod(), observation.getReviewStatus(), observation.getReviewNote(),
                    observation.getObservedAt(), observation.getReviewedAt(), observation.getUpdatedAt());
        }
    }
}
