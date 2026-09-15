package dev.dhruv.jobsearch.ingestion;

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
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class GenericInboxService {

    static final String PARSER_VERSION = "generic-inbox-v1";

    private final GenericInboxItemRepository items;
    private final GenericInboxCandidateRepository candidates;
    private final InboxDuplicateMatchRepository duplicateMatches;
    private final OpportunityService opportunityService;
    private final ObjectMapper objectMapper;

    public GenericInboxService(GenericInboxItemRepository items, GenericInboxCandidateRepository candidates,
            InboxDuplicateMatchRepository duplicateMatches, OpportunityService opportunityService, ObjectMapper objectMapper) {
        this.items = items;
        this.candidates = candidates;
        this.duplicateMatches = duplicateMatches;
        this.opportunityService = opportunityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateResult receive(CreateCommand command) {
        String content = requireContent(command.content());
        String contentHash = hash(content);
        var replay = items.findByContentHash(contentHash);
        if (replay.isPresent()) return new CreateResult(true, view(replay.get()));

        GenericInboxItem item = items.save(new GenericInboxItem(
                command.sourceType(), limited(command.sourceLabel(), 160), limited(command.sourceFilename(), 500),
                limited(command.mediaType(), 120), contentHash, content, PARSER_VERSION));
        try {
            List<ParsedCandidate> parsed = parse(command.sourceType(), content);
            if (parsed.isEmpty()) throw new IllegalArgumentException("No job candidates were found in this source.");
            int row = 1;
            for (ParsedCandidate value : parsed) {
                candidates.save(toCandidate(item, row++, value, command.sourceLabel()));
            }
            item.parsed(parsed.size());
            item.updateReviewStatus(GenericInboxItemStatus.NEEDS_REVIEW);
        } catch (Exception exception) {
            item.fail(rootMessage(exception));
        }
        return new CreateResult(false, view(item));
    }

    @Transactional(readOnly = true)
    public List<InboxItemView> list() {
        return items.findTop30ByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public InboxItemView get(UUID itemId) {
        return view(items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Inbox item " + itemId + " was not found.")));
    }

    @Transactional
    public InboxCandidateView revise(UUID candidateId, ReviseCandidate command) {
        GenericInboxCandidate candidate = candidate(candidateId);
        candidate.revise(
                limited(command.companyName(), 240), limited(command.roleTitle(), 240),
                limited(command.location(), 240), command.workMode(), limited(command.sourceName(), 120),
                limited(command.sourceExternalId(), 240), limited(command.sourceUrl(), 1500), trimToNull(command.description()));
        return candidateView(candidate);
    }

    @Transactional
    public InboxCandidateView importCandidate(UUID candidateId) {
        GenericInboxCandidate candidate = candidate(candidateId);
        if (candidate.getStatus() == GenericInboxCandidateStatus.DUPLICATE_REVIEW) {
            throw new IllegalStateException("Resolve the possible duplicate before importing this candidate.");
        }
        if (trimToNull(candidate.getCompanyName()) == null || trimToNull(candidate.getRoleTitle()) == null) {
            throw new IllegalArgumentException("Company and role title are required before importing this candidate.");
        }
        var opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                candidate.getCompanyName(), candidate.getRoleTitle(), candidate.getLocation(), candidate.getWorkMode(),
                firstNonBlank(candidate.getSourceName(), candidate.getInboxItem().getSourceLabel(), "Generic inbox"),
                candidate.getSourceUrl(), candidate.getDescription(), Instant.now()));
        if (trimToNull(candidate.getSourceExternalId()) != null) {
            opportunity = opportunityService.recordSourceExternalId(opportunity.getId(), candidate.getSourceExternalId());
        }
        candidate.importAs(opportunity);
        refreshItemStatus(candidate.getInboxItem());
        return candidateView(candidate);
    }

    @Transactional
    public InboxCandidateView rejectCandidate(UUID candidateId) {
        GenericInboxCandidate candidate = candidate(candidateId);
        candidate.reject();
        refreshItemStatus(candidate.getInboxItem());
        return candidateView(candidate);
    }

    @Transactional(readOnly = true)
    public AssistanceContext assistanceContext(UUID candidateId) {
        GenericInboxCandidate candidate = candidate(candidateId);
        return new AssistanceContext(candidate.getId(), candidate.getInboxItem().getId(), candidate.getRawPayload(),
                candidate.getCompanyName(), candidate.getRoleTitle(), candidate.getLocation(), candidate.getWorkMode(),
                candidate.getSourceName(), candidate.getSourceExternalId(), candidate.getSourceUrl(),
                candidate.getDescription(), candidate.getOpportunity() == null ? null : candidate.getOpportunity().getId());
    }

    @Transactional
    public InboxCandidateView applyAssistedFields(UUID candidateId, AssistedFields suggestion, Set<String> selectedFields) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            throw new IllegalArgumentException("Select at least one suggested field to apply.");
        }
        GenericInboxCandidate candidate = candidate(candidateId);
        candidate.revise(
                selectedFields.contains("companyName") ? limited(suggestion.companyName(), 240) : candidate.getCompanyName(),
                selectedFields.contains("roleTitle") ? limited(suggestion.roleTitle(), 240) : candidate.getRoleTitle(),
                selectedFields.contains("location") ? limited(suggestion.location(), 240) : candidate.getLocation(),
                selectedFields.contains("workMode") ? suggestion.workMode() : candidate.getWorkMode(),
                selectedFields.contains("sourceName") ? limited(suggestion.sourceName(), 120) : candidate.getSourceName(),
                selectedFields.contains("sourceExternalId") ? limited(suggestion.sourceExternalId(), 240) : candidate.getSourceExternalId(),
                selectedFields.contains("sourceUrl") ? limited(suggestion.sourceUrl(), 1500) : candidate.getSourceUrl(),
                selectedFields.contains("description") ? trimToNull(suggestion.description()) : candidate.getDescription());
        return candidateView(candidate);
    }

    private GenericInboxCandidate toCandidate(GenericInboxItem item, int row, ParsedCandidate parsed,
            String fallbackSourceLabel) {
        String company = limited(value(parsed.values(), "companyname", "company", "employer", "organization"), 240);
        String title = limited(value(parsed.values(), "roletitle", "jobtitle", "title", "role"), 240);
        String location = limited(value(parsed.values(), "location", "joblocation"), 240);
        String source = limited(firstNonBlank(value(parsed.values(), "sourcename", "source"), fallbackSourceLabel), 120);
        String sourceExternalId = limited(value(parsed.values(), "sourceexternalid", "externalid", "jobid",
                "requisitionid", "requisitionnumber", "reqid"), 240);
        String url = limited(value(parsed.values(), "sourceurl", "joburl", "url", "link"), 1500);
        String description = trimToNull(value(parsed.values(), "description", "jobdescription", "summary", "details"));
        WorkMode mode = parseWorkMode(value(parsed.values(), "workmode", "workarrangement", "mode"));
        String warnings = GenericInboxCandidate.missingFieldWarnings(company, title);
        return new GenericInboxCandidate(item, row, company, title, location, mode, source, sourceExternalId, url, description,
                warnings, parsed.rawPayload());
    }

    private List<ParsedCandidate> parse(GenericInboxSourceType sourceType, String content) throws JacksonException {
        return switch (sourceType) {
            case PASTED_JSON, UPLOADED_JSON -> parseJson(content);
            case UPLOADED_CSV -> parseCsv(content);
            case PASTED_TEXT -> looksLikeJson(content) ? parseJson(content) : parseText(content);
        };
    }

    private List<ParsedCandidate> parseJson(String content) throws JacksonException {
        JsonNode root = objectMapper.readTree(content);
        JsonNode records = root;
        if (root.isObject()) {
            for (String container : List.of("jobs", "opportunities", "items", "records")) {
                if (root.has(container) && root.get(container).isArray()) {
                    records = root.get(container);
                    break;
                }
            }
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (records.isArray()) records.forEach(nodes::add);
        else nodes.add(records);

        List<ParsedCandidate> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) continue;
            Map<String, String> values = new LinkedHashMap<>();
            node.forEachEntry((key, value) -> {
                if (value.isValueNode()) values.put(normalizeKey(key), value.isTextual() ? value.stringValue() : value.toString());
            });
            result.add(new ParsedCandidate(values, objectMapper.writeValueAsString(node)));
        }
        return result;
    }

    private List<ParsedCandidate> parseText(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder descriptionTail = new StringBuilder();
        boolean readingDescription = false;
        for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            int separator = line.indexOf(':');
            if (!readingDescription && separator > 0) {
                String key = normalizeKey(line.substring(0, separator));
                String value = line.substring(separator + 1).trim();
                if (isKnownKey(key)) {
                    values.put(key, value);
                    readingDescription = key.equals("description") || key.equals("jobdescription") || key.equals("details");
                    continue;
                }
            }
            if (readingDescription) {
                if (!descriptionTail.isEmpty()) descriptionTail.append('\n');
                descriptionTail.append(line);
            }
        }
        if (!descriptionTail.isEmpty()) {
            String existing = value(values, "description", "jobdescription", "details");
            values.put("description", firstNonBlank(existing, "") + (existing == null ? "" : "\n") + descriptionTail);
        }
        if (values.isEmpty()) values.put("description", content);
        return List.of(new ParsedCandidate(values, content));
    }

    private List<ParsedCandidate> parseCsv(String content) {
        List<List<String>> rows = csvRows(content);
        if (rows.size() < 2) throw new IllegalArgumentException("CSV input must include a header and at least one data row.");
        List<String> headers = rows.getFirst().stream().map(this::normalizeKey).toList();
        List<ParsedCandidate> result = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isBlank)) continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < Math.min(headers.size(), row.size()); column++) {
                if (!headers.get(column).isBlank()) values.put(headers.get(column), row.get(column));
            }
            result.add(new ParsedCandidate(values, csvLine(row)));
        }
        return result;
    }

    private List<List<String>> csvRows(String input) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < input.length() && input.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (current == '"') quoted = false;
                else field.append(current);
            } else if (current == '"') quoted = true;
            else if (current == ',') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') index++;
                row.add(field.toString().trim());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else field.append(current);
        }
        if (quoted) throw new IllegalArgumentException("CSV input contains an unterminated quoted field.");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private String csvLine(List<String> values) {
        return values.stream().map(value -> '"' + value.replace("\"", "\"\"") + '"')
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private void refreshItemStatus(GenericInboxItem item) {
        List<GenericInboxCandidate> itemCandidates = candidates.findByInboxItemIdOrderByRowNumber(item.getId());
        long imported = itemCandidates.stream().filter(value -> value.getStatus() == GenericInboxCandidateStatus.IMPORTED
                || value.getStatus() == GenericInboxCandidateStatus.LINKED
                || value.getStatus() == GenericInboxCandidateStatus.MERGED).count();
        long rejected = itemCandidates.stream().filter(value -> value.getStatus() == GenericInboxCandidateStatus.REJECTED).count();
        if (imported == itemCandidates.size()) item.updateReviewStatus(GenericInboxItemStatus.IMPORTED);
        else if (rejected == itemCandidates.size()) item.updateReviewStatus(GenericInboxItemStatus.REJECTED);
        else if (imported + rejected > 0) item.updateReviewStatus(GenericInboxItemStatus.PARTIALLY_REVIEWED);
        else item.updateReviewStatus(GenericInboxItemStatus.NEEDS_REVIEW);
    }

    private GenericInboxCandidate candidate(UUID id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("Inbox candidate " + id + " was not found."));
    }

    private InboxItemView view(GenericInboxItem item) {
        return new InboxItemView(item.getId(), item.getSourceType(), item.getSourceLabel(), item.getSourceFilename(),
                item.getMediaType(), item.getContentHash(), item.getParserVersion(), item.getStatus(),
                item.getCandidateCount(), item.getErrorMessage(), item.getCreatedAt(), item.getUpdatedAt(),
                candidates.findByInboxItemIdOrderByRowNumber(item.getId()).stream().map(this::candidateView).toList());
    }

    private InboxCandidateView candidateView(GenericInboxCandidate candidate) {
        return InboxCandidateView.from(candidate, duplicateMatches.findByCandidateIdOrderByConfidenceDesc(candidate.getId()));
    }

    private boolean looksLikeJson(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean isKnownKey(String key) {
        return List.of("company", "companyname", "employer", "organization", "title", "role", "roletitle",
                "jobtitle", "location", "joblocation", "workmode", "workarrangement", "mode", "sourcename",
                "source", "sourceurl", "joburl", "url", "link", "description", "jobdescription", "summary",
                "details", "sourceexternalid", "externalid", "jobid", "requisitionid", "requisitionnumber",
                "reqid").contains(key);
    }

    private String value(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = trimToNull(values.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private WorkMode parseWorkMode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return WorkMode.UNSPECIFIED;
        String upper = normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (upper.contains("REMOTE")) return WorkMode.REMOTE;
        if (upper.contains("HYBRID")) return WorkMode.HYBRID;
        if (upper.contains("ONSITE") || upper.contains("ON_SITE") || upper.contains("OFFICE")) return WorkMode.ONSITE;
        return WorkMode.UNSPECIFIED;
    }

    private String normalizeKey(String value) {
        if (value == null) return "";
        return value.replace("\uFEFF", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String requireContent(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Inbox source content is required.");
        return value;
    }

    private String limited(String value, int limit) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) return trimmed;
        }
        return null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ParsedCandidate(Map<String, String> values, String rawPayload) {}

    public record CreateCommand(GenericInboxSourceType sourceType, String sourceLabel, String sourceFilename,
            String mediaType, String content) {}

    public record ReviseCandidate(String companyName, String roleTitle, String location, WorkMode workMode,
            String sourceName, String sourceExternalId, String sourceUrl, String description) {}

    public record AssistanceContext(UUID candidateId, UUID inboxItemId, String rawPayload, String companyName,
            String roleTitle, String location, WorkMode workMode, String sourceName, String sourceExternalId,
            String sourceUrl, String description, UUID opportunityId) {}

    public record AssistedFields(String companyName, String roleTitle, String location, WorkMode workMode,
            String sourceName, String sourceExternalId, String sourceUrl, String description) {}

    public record CreateResult(boolean replayed, InboxItemView item) {}

    public record InboxItemView(UUID id, GenericInboxSourceType sourceType, String sourceLabel, String sourceFilename,
            String mediaType, String contentHash, String parserVersion, GenericInboxItemStatus status,
            int candidateCount, String errorMessage, Instant createdAt, Instant updatedAt,
            List<InboxCandidateView> candidates) {
    }

    public record InboxCandidateView(UUID id, UUID inboxItemId, int rowNumber, String companyName, String roleTitle, String location,
            WorkMode workMode, String sourceName, String sourceExternalId, String sourceUrl, String description, String parseWarnings,
            GenericInboxCandidateStatus status, UUID opportunityId, Instant reviewedAt, Instant createdAt,
            Instant updatedAt, List<InboxDuplicateMatchView> duplicateMatches) {
        static InboxCandidateView from(GenericInboxCandidate candidate, List<InboxDuplicateMatch> matches) {
            return new InboxCandidateView(candidate.getId(), candidate.getInboxItem().getId(), candidate.getRowNumber(), candidate.getCompanyName(),
                    candidate.getRoleTitle(), candidate.getLocation(), candidate.getWorkMode(), candidate.getSourceName(),
                    candidate.getSourceExternalId(), candidate.getSourceUrl(), candidate.getDescription(), candidate.getParseWarnings(), candidate.getStatus(),
                    candidate.getOpportunity() == null ? null : candidate.getOpportunity().getId(), candidate.getReviewedAt(),
                    candidate.getCreatedAt(), candidate.getUpdatedAt(), matches.stream().map(InboxDuplicateMatchView::from).toList());
        }
    }

    public record InboxDuplicateMatchView(UUID id, UUID opportunityId, String companyName, String roleTitle,
            String location, WorkMode workMode, String sourceName, String sourceExternalId, String sourceUrl,
            String description, DuplicateMatchType matchType, int confidence, String explanation,
            DuplicateMatchStatus status) {
        static InboxDuplicateMatchView from(InboxDuplicateMatch match) {
            var opportunity = match.getOpportunity();
            return new InboxDuplicateMatchView(match.getId(), opportunity.getId(), opportunity.getCompanyName(),
                    opportunity.getRoleTitle(), opportunity.getLocation(), opportunity.getWorkMode(),
                    opportunity.getSourceName(), opportunity.getSourceExternalId(), opportunity.getSourceUrl(),
                    opportunity.getDescription(), match.getMatchType(), match.getConfidence(),
                    match.getExplanation(), match.getStatus());
        }
    }
}
