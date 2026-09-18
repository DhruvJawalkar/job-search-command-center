package dev.dhruv.jobsearch.assistance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.connected.TransmissionOperation;
import dev.dhruv.jobsearch.connected.TransmissionOutcome;
import dev.dhruv.jobsearch.connected.TransmissionService;
import dev.dhruv.jobsearch.ingestion.DuplicateReviewService;
import dev.dhruv.jobsearch.ingestion.GenericInboxService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;
import dev.dhruv.jobsearch.review.WeeklyReviewService;
import dev.dhruv.jobsearch.shared.NotFoundException;
import dev.dhruv.jobsearch.skill.SkillEvidenceService;
import dev.dhruv.jobsearch.skill.SkillStrength;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistanceService {

    static final String INBOX_PROMPT = "inbox-structuring-v1";
    static final String INBOX_SCHEMA = "inbox-structuring-schema-v1";
    static final String WEEKLY_PROMPT = "weekly-reflection-v1";
    static final String WEEKLY_SCHEMA = "weekly-reflection-schema-v1";

    private static final Set<String> ALLOWED_FIELDS = Set.of("companyName", "roleTitle", "location", "workMode",
            "sourceName", "sourceExternalId", "sourceUrl", "description");
    private static final String OPENAI_DESTINATION = "https://api.openai.com/v1/responses";

    private final AssistanceRunRepository runs;
    private final AssistanceDecisionRepository decisions;
    private final AssistanceProvider provider;
    private final GenericInboxService inbox;
    private final DuplicateReviewService duplicateReview;
    private final WeeklyReviewService weeklyReviews;
    private final SkillEvidenceService skillEvidence;
    private final ObjectMapper objectMapper;
    private final PrivacyPolicyService privacyPolicy;
    private final SessionAssistanceStore sessionStore;
    private final StatelessAssistanceArtifactService statelessArtifacts;
    private final TransmissionService transmissions;

    public AssistanceService(AssistanceRunRepository runs, AssistanceDecisionRepository decisions,
            AssistanceProvider provider, GenericInboxService inbox, DuplicateReviewService duplicateReview,
            WeeklyReviewService weeklyReviews, SkillEvidenceService skillEvidence, ObjectMapper objectMapper,
            PrivacyPolicyService privacyPolicy, SessionAssistanceStore sessionStore,
            StatelessAssistanceArtifactService statelessArtifacts, TransmissionService transmissions) {
        this.runs = runs;
        this.decisions = decisions;
        this.provider = provider;
        this.inbox = inbox;
        this.duplicateReview = duplicateReview;
        this.weeklyReviews = weeklyReviews;
        this.skillEvidence = skillEvidence;
        this.objectMapper = objectMapper;
        this.privacyPolicy = privacyPolicy;
        this.sessionStore = sessionStore;
        this.statelessArtifacts = statelessArtifacts;
        this.transmissions = transmissions;
    }

    public ConfigurationView configuration() {
        return new ConfigurationView(provider.configured(), provider.providerName(), provider.model(),
                provider.configured() ? "Ready for explicit, review-controlled requests."
                        : "Start the reviewed connected profile with its broker token, provider key, and model.");
    }

    @Transactional(readOnly = true)
    public InboxAssistanceOverview inboxOverview(UUID candidateId) {
        var context = inbox.assistanceContext(candidateId);
        var mode = privacyPolicy.assistanceContextMode();
        return new InboxAssistanceOverview(configuration(), context.rawPayload(), context,
                visibleRuns(mode, AssistanceUseCase.INBOX_STRUCTURING, candidateId)
                        .stream().map(this::inboxView).toList());
    }

    @Transactional
    public TransmissionService.PreviewView previewInboxTransmission(UUID candidateId) {
        String input = json(inbox.assistanceContext(candidateId));
        return transmissions.issue(TransmissionOperation.OPENAI_INBOX_STRUCTURING, OPENAI_DESTINATION,
                "Structure one reviewed job-opening candidate for local review",
                List.of("companyName", "roleTitle", "location", "workMode", "sourceName", "sourceExternalId",
                        "sourceUrl", "description"), input);
    }

    @Transactional
    public GenerateInboxResult generateInbox(UUID candidateId, String confirmationToken) {
        requireConfigured();
        var context = inbox.assistanceContext(candidateId);
        String input = json(context);
        String inputHash = sha256(input);
        var mode = privacyPolicy.assistanceContextMode();
        var existing = findReplay(mode, AssistanceUseCase.INBOX_STRUCTURING, candidateId, inputHash,
                INBOX_PROMPT, INBOX_SCHEMA);
        if (existing.isPresent()) return new GenerateInboxResult(true, inboxView(existing.get()));
        var transmission = transmissions.consume(confirmationToken, TransmissionOperation.OPENAI_INBOX_STRUCTURING,
                OPENAI_DESTINATION, input);

        AssistanceRun run = new AssistanceRun(AssistanceUseCase.INBOX_STRUCTURING, candidateId, inputHash,
                provider.providerName(), provider.model(), INBOX_PROMPT, INBOX_SCHEMA);
        run = retain(mode, run);
        TransmissionOutcome outcome;
        try {
            var result = provider.generate(inboxInstructions(), input, "job_inbox_suggestion", schema(INBOX_JSON_SCHEMA));
            InboxSuggestion suggestion = objectMapper.readValue(result.outputJson(), InboxSuggestion.class);
            validate(suggestion);
            run.complete(json(suggestion), result.responseId(), result.inputTokens(), result.outputTokens());
            outcome = TransmissionOutcome.SUCCESS;
        } catch (Exception exception) {
            run.fail(rootMessage(exception));
            outcome = TransmissionOutcome.FAILED;
        }
        transmissions.record(transmission, outcome);
        String statelessSaveArtifact = mode == dev.dhruv.jobsearch.privacy.AssistanceContextMode.STATELESS
                && run.getStatus() == AssistanceRunStatus.COMPLETED
                ? statelessArtifacts.issue(AssistanceUseCase.INBOX_STRUCTURING, candidateId,
                        inputHash,
                        run.getResultPayload())
                : null;
        return new GenerateInboxResult(false, inboxView(run, statelessSaveArtifact));
    }

    @Transactional
    public InboxAssistanceView applyFields(UUID runId, Set<String> selectedFields) {
        AssistanceRun run = completed(runId, AssistanceUseCase.INBOX_STRUCTURING);
        if (selectedFields == null || selectedFields.isEmpty() || !ALLOWED_FIELDS.containsAll(selectedFields)) {
            throw new IllegalArgumentException("Choose one or more supported fields from this suggestion.");
        }
        InboxSuggestion suggestion = inboxSuggestion(run);
        inbox.applyAssistedFields(run.getTargetId(), suggestion.fields(), selectedFields);
        recordDecision(run, AssistanceDecisionType.FIELDS_APPLIED,
                json(selectedFields.stream().sorted().toList()), "Selected AI suggestions applied to the review candidate.");
        duplicateReview.scanItem(inbox.assistanceContext(run.getTargetId()).inboxItemId());
        return inboxView(run);
    }

    @Transactional
    public PublishSkillsResult publishSkills(UUID runId) {
        AssistanceRun run = completed(runId, AssistanceUseCase.INBOX_STRUCTURING);
        var context = inbox.assistanceContext(run.getTargetId());
        if (context.opportunityId() == null) {
            throw new IllegalStateException("Import or link this candidate to an opportunity before publishing proposed skill evidence.");
        }
        InboxSuggestion suggestion = inboxSuggestion(run);
        var result = skillEvidence.publishAssistedEvidence(context.opportunityId(),
                suggestion.fields().description() == null ? context.rawPayload() : suggestion.fields().description(),
                suggestion.skills().stream().map(skill -> new SkillEvidenceService.AssistedSkill(
                        skill.name(), skill.strength(), skill.evidenceSnippet())).toList());
        recordDecision(run, AssistanceDecisionType.SKILLS_PUBLISHED, null,
                result.published().size() + " proposed observations published; " + result.unmatched().size() + " unmatched.");
        return new PublishSkillsResult(result.published().size(), result.unmatched(), inboxView(run));
    }

    @Transactional
    public InboxAssistanceView dismiss(UUID runId) {
        AssistanceRun run = completed(runId, AssistanceUseCase.INBOX_STRUCTURING);
        recordDecision(run, AssistanceDecisionType.DISMISSED, null,
                "Assistance result dismissed; source and result retained.");
        return inboxView(run);
    }

    @Transactional(readOnly = true)
    public WeeklyAssistanceOverview weeklyOverview(UUID reviewId) {
        var review = weeklyReviews.get(reviewId);
        var mode = privacyPolicy.assistanceContextMode();
        return new WeeklyAssistanceOverview(configuration(), review,
                visibleRuns(mode, AssistanceUseCase.WEEKLY_REFLECTION, reviewId)
                        .stream().map(this::weeklyView).toList());
    }

    @Transactional
    public StatelessApplyResult applyStatelessFields(UUID candidateId, String artifact, Set<String> selectedFields) {
        requireStateless();
        if (selectedFields == null || selectedFields.isEmpty() || !ALLOWED_FIELDS.containsAll(selectedFields)) {
            throw new IllegalArgumentException("Choose one or more supported fields from this suggestion.");
        }
        var context = inbox.assistanceContext(candidateId);
        var payload = statelessArtifacts.verify(artifact, AssistanceUseCase.INBOX_STRUCTURING, candidateId,
                sha256(json(context)));
        InboxSuggestion suggestion = read(payload.resultPayload(), InboxSuggestion.class);
        inbox.applyAssistedFields(candidateId, suggestion.fields(), selectedFields);
        duplicateReview.scanItem(inbox.assistanceContext(candidateId).inboxItemId());
        return new StatelessApplyResult(candidateId, selectedFields.stream().sorted().toList(), Instant.now());
    }

    @Transactional
    public StatelessPublishSkillsResult publishStatelessSkills(UUID candidateId, String artifact) {
        requireStateless();
        var context = inbox.assistanceContext(candidateId);
        var payload = statelessArtifacts.verify(artifact, AssistanceUseCase.INBOX_STRUCTURING, candidateId,
                sha256(json(context)));
        if (context.opportunityId() == null) {
            throw new IllegalStateException("Import or link this candidate to an opportunity before publishing proposed skill evidence.");
        }
        InboxSuggestion suggestion = read(payload.resultPayload(), InboxSuggestion.class);
        var result = skillEvidence.publishAssistedEvidence(context.opportunityId(),
                suggestion.fields().description() == null ? context.rawPayload() : suggestion.fields().description(),
                suggestion.skills().stream().map(skill -> new SkillEvidenceService.AssistedSkill(
                        skill.name(), skill.strength(), skill.evidenceSnippet())).toList());
        return new StatelessPublishSkillsResult(candidateId, result.published().size(), result.unmatched(), Instant.now());
    }

    @Transactional
    public TransmissionService.PreviewView previewWeeklyTransmission(UUID reviewId) {
        String input = json(weeklyReviews.get(reviewId));
        return transmissions.issue(TransmissionOperation.OPENAI_WEEKLY_REFLECTION, OPENAI_DESTINATION,
                "Draft a weekly reflection from selected local review evidence",
                List.of("reviewPeriod", "metrics", "deltas", "existingRevisions"), input);
    }

    @Transactional
    public GenerateWeeklyResult generateWeekly(UUID reviewId, String confirmationToken) {
        requireConfigured();
        var review = weeklyReviews.get(reviewId);
        String input = json(review);
        String inputHash = sha256(input);
        var mode = privacyPolicy.assistanceContextMode();
        var existing = findReplay(mode, AssistanceUseCase.WEEKLY_REFLECTION, reviewId, inputHash,
                WEEKLY_PROMPT, WEEKLY_SCHEMA);
        if (existing.isPresent()) return new GenerateWeeklyResult(true, weeklyView(existing.get()));
        var transmission = transmissions.consume(confirmationToken, TransmissionOperation.OPENAI_WEEKLY_REFLECTION,
                OPENAI_DESTINATION, input);

        AssistanceRun run = new AssistanceRun(AssistanceUseCase.WEEKLY_REFLECTION, reviewId, inputHash,
                provider.providerName(), provider.model(), WEEKLY_PROMPT, WEEKLY_SCHEMA);
        run = retain(mode, run);
        TransmissionOutcome outcome;
        try {
            var result = provider.generate(weeklyInstructions(), input, "weekly_reflection_draft", schema(WEEKLY_JSON_SCHEMA));
            WeeklyDraft draft = objectMapper.readValue(result.outputJson(), WeeklyDraft.class);
            if (allBlank(draft.wins(), draft.challenges(), draft.reflection(), draft.nextWeekAdjustments(), draft.nextWeekFocus())) {
                throw new IllegalArgumentException("The weekly draft did not contain a reflection.");
            }
            run.complete(json(draft), result.responseId(), result.inputTokens(), result.outputTokens());
            outcome = TransmissionOutcome.SUCCESS;
        } catch (Exception exception) {
            run.fail(rootMessage(exception));
            outcome = TransmissionOutcome.FAILED;
        }
        transmissions.record(transmission, outcome);
        return new GenerateWeeklyResult(false, weeklyView(run));
    }

    private Optional<AssistanceRun> findReplay(dev.dhruv.jobsearch.privacy.AssistanceContextMode mode,
            AssistanceUseCase useCase, UUID targetId, String inputHash, String promptVersion, String schemaVersion) {
        return switch (mode) {
            case STATELESS -> Optional.empty();
            case SESSION_ONLY -> sessionStore.findReplay(useCase, targetId, inputHash, provider.providerName(),
                    provider.model(), promptVersion, schemaVersion);
            case TIME_BOUND -> runs.findByUseCaseAndTargetIdAndInputHashAndProviderAndModelAndPromptVersionAndSchemaVersion(
                    useCase, targetId, inputHash, provider.providerName(), provider.model(), promptVersion, schemaVersion);
        };
    }

    private List<AssistanceRun> visibleRuns(dev.dhruv.jobsearch.privacy.AssistanceContextMode mode,
            AssistanceUseCase useCase, UUID targetId) {
        return switch (mode) {
            case STATELESS -> List.of();
            case SESSION_ONLY -> sessionStore.find(useCase, targetId);
            case TIME_BOUND -> runs.findByUseCaseAndTargetIdOrderByCreatedAtDesc(useCase, targetId);
        };
    }

    private AssistanceRun retain(dev.dhruv.jobsearch.privacy.AssistanceContextMode mode, AssistanceRun run) {
        return switch (mode) {
            case STATELESS -> run; // response-only; never enter application-owned durable or session storage
            case SESSION_ONLY -> { sessionStore.put(run); yield run; }
            case TIME_BOUND -> runs.save(run);
        };
    }

    private void recordDecision(AssistanceRun run, AssistanceDecisionType type, String selectedFields, String note) {
        var mode = privacyPolicy.assistanceContextMode();
        if (mode == dev.dhruv.jobsearch.privacy.AssistanceContextMode.SESSION_ONLY) {
            sessionStore.addDecision(run.getId(), new DecisionView(UUID.randomUUID(), type, selectedFields, note,
                    Instant.now()));
            return;
        }
        decisions.save(new AssistanceDecision(run, type, selectedFields, note));
    }

    private List<DecisionView> decisionViews(AssistanceRun run) {
        return switch (privacyPolicy.assistanceContextMode()) {
            case STATELESS -> List.of();
            case SESSION_ONLY -> sessionStore.decisions(run.getId());
            case TIME_BOUND -> decisions.findByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                    .map(DecisionView::from).toList();
        };
    }

    private AssistanceRun completed(UUID runId, AssistanceUseCase expectedUseCase) {
        var mode = privacyPolicy.assistanceContextMode();
        if (mode == dev.dhruv.jobsearch.privacy.AssistanceContextMode.STATELESS) {
            throw new IllegalStateException("Stateless assistance results are response-only and cannot be reused by run ID.");
        }
        AssistanceRun run = (mode == dev.dhruv.jobsearch.privacy.AssistanceContextMode.SESSION_ONLY
                ? sessionStore.find(runId) : runs.findById(runId))
                .orElseThrow(() -> new NotFoundException("Assistance run " + runId + " was not found."));
        if (run.getUseCase() != expectedUseCase || run.getStatus() != AssistanceRunStatus.COMPLETED) {
            throw new IllegalStateException("This completed assistance result is not available for that action.");
        }
        return run;
    }

    private InboxAssistanceView inboxView(AssistanceRun run) {
        return inboxView(run, null);
    }

    private InboxAssistanceView inboxView(AssistanceRun run, String statelessSaveArtifact) {
        InboxSuggestion suggestion = run.getStatus() == AssistanceRunStatus.COMPLETED ? inboxSuggestion(run) : null;
        return new InboxAssistanceView(run.getId(), run.getStatus(), run.getProvider(), run.getModel(),
                run.getPromptVersion(), run.getSchemaVersion(), run.getErrorMessage(), run.getCreatedAt(),
                run.getCompletedAt(), run.getInputTokens(), run.getOutputTokens(), suggestion,
                decisionViews(run), statelessSaveArtifact);
    }

    private WeeklyAssistanceView weeklyView(AssistanceRun run) {
        WeeklyDraft draft = run.getStatus() == AssistanceRunStatus.COMPLETED
                ? read(run.getResultPayload(), WeeklyDraft.class) : null;
        return new WeeklyAssistanceView(run.getId(), run.getStatus(), run.getProvider(), run.getModel(),
                run.getPromptVersion(), run.getErrorMessage(), run.getCreatedAt(), run.getCompletedAt(), draft);
    }

    private InboxSuggestion inboxSuggestion(AssistanceRun run) {
        return read(run.getResultPayload(), InboxSuggestion.class);
    }

    private void validate(InboxSuggestion suggestion) {
        if (suggestion == null || suggestion.fields() == null || suggestion.skills() == null
                || suggestion.warnings() == null || suggestion.reviewQuestions() == null) {
            throw new IllegalArgumentException("The structured suggestion is incomplete.");
        }
        for (SkillSuggestion skill : suggestion.skills()) {
            if (skill.name() == null || skill.name().isBlank() || skill.evidenceSnippet() == null
                    || skill.evidenceSnippet().isBlank()) {
                throw new IllegalArgumentException("Each skill suggestion needs a name and evidence snippet.");
            }
        }
    }

    private void requireConfigured() {
        if (!provider.configured()) throw new IllegalStateException(configuration().message());
    }

    private void requireStateless() {
        if (privacyPolicy.assistanceContextMode() != dev.dhruv.jobsearch.privacy.AssistanceContextMode.STATELESS) {
            throw new IllegalStateException("This response-carried save path is available only in Stateless mode.");
        }
    }

    private String inboxInstructions() {
        return "Extract only facts supported by the supplied job source. Never invent a company, role, URL, job ID, location, qualification, or skill. "
                + "Use null for unsupported scalar fields. Preserve a clean job description. Skill evidence must be a concise verbatim snippet from the source. "
                + "Do not score candidate fit or make an application decision. Return only the requested schema.";
    }

    private String weeklyInstructions() {
        return "Draft a concise weekly job-search reflection using only the supplied immutable metrics, deltas, and existing revisions. "
                + "Do not invent causes, conversations, outcomes, or preparation activity. Clearly distinguish observed evidence from cautious interpretation. "
                + "Return only the requested schema; the user will review before saving.";
    }

    private JsonNode schema(String value) {
        try { return objectMapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Assistance schema is invalid.", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Could not serialize assistance data.", exception); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("Stored assistance output is invalid.", exception); }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private boolean allBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return false;
        return true;
    }

    public record ConfigurationView(boolean configured, String provider, String model, String message) {}
    public record InboxAssistanceOverview(ConfigurationView configuration, String outboundContent,
            GenericInboxService.AssistanceContext current, List<InboxAssistanceView> runs) {}
    public record GenerateInboxResult(boolean replayed, InboxAssistanceView run) {}
    public record InboxAssistanceView(UUID id, AssistanceRunStatus status, String provider, String model,
            String promptVersion, String schemaVersion, String errorMessage, Instant createdAt, Instant completedAt,
            Integer inputTokens, Integer outputTokens, InboxSuggestion suggestion, List<DecisionView> decisions,
            String statelessSaveArtifact) {}
    public record InboxSuggestion(GenericInboxService.AssistedFields fields, List<String> responsibilities,
            List<String> requiredQualifications, List<String> preferredQualifications,
            List<SkillSuggestion> skills, List<String> warnings, List<String> reviewQuestions) {}
    public record SkillSuggestion(String name, SkillStrength strength, String evidenceSnippet) {}
    public record PublishSkillsResult(int publishedCount, List<String> unmatchedSkills, InboxAssistanceView run) {}
    public record StatelessApplyResult(UUID candidateId, List<String> appliedFields, Instant appliedAt) {}
    public record StatelessPublishSkillsResult(UUID candidateId, int publishedCount, List<String> unmatchedSkills,
            Instant publishedAt) {}
    public record WeeklyAssistanceOverview(ConfigurationView configuration, WeeklyReviewService.ReviewView review,
            List<WeeklyAssistanceView> runs) {}
    public record GenerateWeeklyResult(boolean replayed, WeeklyAssistanceView run) {}
    public record WeeklyAssistanceView(UUID id, AssistanceRunStatus status, String provider, String model,
            String promptVersion, String errorMessage, Instant createdAt, Instant completedAt, WeeklyDraft draft) {}
    public record WeeklyDraft(String wins, String challenges, String reflection, String nextWeekAdjustments,
            String nextWeekFocus, List<String> evidence) {}
    public record DecisionView(UUID id, AssistanceDecisionType decisionType, String selectedFields, String note,
            Instant createdAt) {
        static DecisionView from(AssistanceDecision value) {
            return new DecisionView(value.getId(), value.getDecisionType(), value.getSelectedFields(), value.getNote(),
                    value.getCreatedAt());
        }
    }

    private static final String INBOX_JSON_SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["fields","responsibilities","requiredQualifications","preferredQualifications","skills","warnings","reviewQuestions"],
             "properties":{
               "fields":{"type":"object","additionalProperties":false,
                 "required":["companyName","roleTitle","location","workMode","sourceName","sourceExternalId","sourceUrl","description"],
                 "properties":{
                   "companyName":{"type":["string","null"]},"roleTitle":{"type":["string","null"]},
                   "location":{"type":["string","null"]},
                   "workMode":{"type":["string","null"],"enum":["ONSITE","HYBRID","REMOTE","UNSPECIFIED",null]},
                   "sourceName":{"type":["string","null"]},"sourceExternalId":{"type":["string","null"]},
                   "sourceUrl":{"type":["string","null"]},"description":{"type":["string","null"]}}},
               "responsibilities":{"type":"array","items":{"type":"string"}},
               "requiredQualifications":{"type":"array","items":{"type":"string"}},
               "preferredQualifications":{"type":"array","items":{"type":"string"}},
               "skills":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "required":["name","strength","evidenceSnippet"],"properties":{
                   "name":{"type":"string"},"strength":{"type":"string","enum":["REQUIRED","PREFERRED","MENTIONED"]},
                   "evidenceSnippet":{"type":"string"}}}},
               "warnings":{"type":"array","items":{"type":"string"}},
               "reviewQuestions":{"type":"array","items":{"type":"string"}}}}
            """;

    private static final String WEEKLY_JSON_SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["wins","challenges","reflection","nextWeekAdjustments","nextWeekFocus","evidence"],
             "properties":{
               "wins":{"type":["string","null"]},"challenges":{"type":["string","null"]},
               "reflection":{"type":["string","null"]},"nextWeekAdjustments":{"type":["string","null"]},
               "nextWeekFocus":{"type":["string","null"]},
               "evidence":{"type":"array","items":{"type":"string"}}}}
            """;
}
