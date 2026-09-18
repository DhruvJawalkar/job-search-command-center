package dev.dhruv.jobsearch.privacy;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PrivacyPolicyService {

    public static final String CURRENT_NOTICE_VERSION = "v1";
    private static final ReentrantLock CLEANUP_LOCK = new ReentrantLock();

    private final PrivacyPolicyRepository policies;
    private final PrivacyCleanupReceiptRepository receipts;
    private final PrivacyAssistanceCleanupRepository assistanceCleanup;
    private final TransientRetentionService transientRetention;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public PrivacyPolicyService(PrivacyPolicyRepository policies, PrivacyCleanupReceiptRepository receipts,
            PrivacyAssistanceCleanupRepository assistanceCleanup, TransientRetentionService transientRetention,
            ApplicationEventPublisher events, PlatformTransactionManager transactionManager) {
        this(policies, receipts, assistanceCleanup, transientRetention, events,
                cleanupTransactions(transactionManager), Clock.systemUTC());
    }

    PrivacyPolicyService(PrivacyPolicyRepository policies, PrivacyCleanupReceiptRepository receipts,
            PrivacyAssistanceCleanupRepository assistanceCleanup, TransientRetentionService transientRetention,
            ApplicationEventPublisher events, TransactionTemplate transactions, Clock clock) {
        this.policies = policies;
        this.receipts = receipts;
        this.assistanceCleanup = assistanceCleanup;
        this.transientRetention = transientRetention;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    PrivacyPolicyService(PrivacyPolicyRepository policies, PrivacyCleanupReceiptRepository receipts,
            PrivacyAssistanceCleanupRepository assistanceCleanup, TransientRetentionService transientRetention,
            PlatformTransactionManager transactionManager, Clock clock) {
        this(policies, receipts, assistanceCleanup, transientRetention, ignored -> {},
                cleanupTransactions(transactionManager), clock);
    }

    @Transactional
    public PolicyView get() { return PolicyView.from(current()); }

    @Transactional
    public PolicyView update(UpdatePolicy command) {
        PrivacyPolicy.validate(command.assistanceContextMode(), command.derivedContextRetentionDays());
        if (!command.consentAccepted() || !CURRENT_NOTICE_VERSION.equals(command.consentTextVersion())) {
            throw new IllegalArgumentException("Accept the current privacy notice version before saving this policy.");
        }
        PrivacyPolicy policy = current();
        AssistanceContextMode previousMode = policy.getAssistanceContextMode();
        int transientDays = command.transientIngestionRetentionDays() == null
                ? policy.getTransientIngestionRetentionDays() : command.transientIngestionRetentionDays();
        PrivacyPolicy.validateTransient(transientDays);
        policy.update(command.assistanceContextMode(), command.derivedContextRetentionDays(), transientDays,
                command.connectedAssistanceEnabled(), command.consentTextVersion(), clock.instant());
        PolicyView saved = PolicyView.from(policies.save(policy));
        if (previousMode != command.assistanceContextMode()) {
            events.publishEvent(new AssistanceContextModeChanged(previousMode, command.assistanceContextMode()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean connectedAssistanceEnabled() {
        return policies.findById(PrivacyPolicy.SINGLETON_ID)
                .map(PrivacyPolicy::isConnectedAssistanceEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public AssistanceContextMode assistanceContextMode() {
        return policies.findById(PrivacyPolicy.SINGLETON_ID)
                .map(PrivacyPolicy::getAssistanceContextMode).orElse(AssistanceContextMode.STATELESS);
    }

    @Transactional
    public CleanupPreview previewCleanup() {
        PrivacyPolicy policy = current();
        Instant cutoff = cutoff(policy, clock.instant());
        Instant transientCutoff = transientCutoff(policy, clock.instant());
        return preview(policy, policy.getAssistanceContextMode(), cutoff, transientCutoff, PreviewBasis.CURRENT_POLICY);
    }

    @Transactional
    public CleanupPreview previewProposedCleanup(AssistanceContextMode mode, Integer retentionDays,
            Integer transientRetentionDays) {
        PrivacyPolicy.validate(mode, retentionDays);
        // A proposed preview must never create or update policy state.
        PrivacyPolicy policy = policies.findById(PrivacyPolicy.SINGLETON_ID).orElseGet(PrivacyPolicy::new);
        int transientDays = transientRetentionDays == null
                ? policy.getTransientIngestionRetentionDays() : transientRetentionDays;
        PrivacyPolicy.validateTransient(transientDays);
        Instant cutoff = cutoff(mode, retentionDays, clock.instant());
        return preview(policy, mode, cutoff, clock.instant().minus(transientDays, ChronoUnit.DAYS),
                PreviewBasis.PROPOSED_POLICY);
    }

    public CleanupReceiptView cleanup() {
        return executeCleanup(clock.instant());
    }

    public Optional<CleanupReceiptView> automaticCleanupIfEligible(boolean startup) {
        Optional<PrivacyPolicy> stored = policies.findById(PrivacyPolicy.SINGLETON_ID);
        if (stored.isEmpty() || !stored.get().isAutomaticCleanupEligible()) return Optional.empty();

        PrivacyPolicy policy = stored.get();
        Instant now = clock.instant();
        if (!startup && policy.getNextScheduledCleanupAt() != null
                && policy.getNextScheduledCleanupAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(executeCleanup(now));
    }

    private CleanupReceiptView executeCleanup(Instant completedAt) {
        CLEANUP_LOCK.lock();
        try {
            DatabaseCleanupPhase database = transactions.execute(status -> executeDatabaseCleanup(completedAt));
            if (database == null) throw new IllegalStateException("The database cleanup phase did not complete.");
            var transientFiles = transientRetention.deleteFiles(database.transientCutoff());
            CleanupReceiptView finalized = transactions.execute(status -> finalizeCleanup(database, transientFiles, completedAt));
            if (finalized == null) throw new IllegalStateException("The cleanup receipt could not be finalized.");
            return finalized;
        } finally {
            CLEANUP_LOCK.unlock();
        }
    }

    private static TransactionTemplate cleanupTransactions(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private DatabaseCleanupPhase executeDatabaseCleanup(Instant completedAt) {
        PrivacyPolicy policy = current();
        Instant cutoff = cutoff(policy, completedAt);
        Instant transientCutoff = transientCutoff(policy, completedAt);
        CleanupPreview plan = preview(policy, policy.getAssistanceContextMode(), cutoff, transientCutoff,
                PreviewBasis.CURRENT_POLICY);

        int decisionsDeleted = assistanceCleanup.deleteDecisionsForRunsBefore(cutoff);
        int runsDeleted = assistanceCleanup.deleteRunsBefore(cutoff);
        if (runsDeleted != plan.assistanceRunCount() || decisionsDeleted != plan.assistanceDecisionCount()) {
            throw new IllegalStateException("Cleanup counts changed while the deletion plan was running; no receipt was recorded.");
        }

        Instant auditCutoff = completedAt.minus(90, ChronoUnit.DAYS);
        var transientDatabase = transientRetention.deleteDatabase(transientCutoff, auditCutoff, completedAt);
        if (transientDatabase.recordCount() != plan.transientDatabaseRecordCount()
                || transientDatabase.auditMetadataRecordCount() != plan.auditMetadataRecordCount()) {
            throw new IllegalStateException("Transient cleanup counts changed while the deletion plan was running; no receipt was recorded.");
        }
        PrivacyCleanupReceipt receipt = receipts.save(new PrivacyCleanupReceipt(policy.getRevision(), cutoff,
                runsDeleted, decisionsDeleted, transientCutoff, transientDatabase.recordCount(),
                transientDatabase.auditMetadataRecordCount(), 0, 0, PrivacyCleanupReceipt.PARTIAL, completedAt));
        return new DatabaseCleanupPhase(receipt.getId(), policy.getRevision(), transientCutoff);
    }

    private CleanupReceiptView finalizeCleanup(DatabaseCleanupPhase database,
            TransientRetentionService.FileDeletion transientFiles, Instant completedAt) {
        PrivacyCleanupReceipt receipt = receipts.findById(database.receiptId())
                .orElseThrow(() -> new IllegalStateException("The cleanup receipt was not available for finalization."));
        receipt.finishFileDeletion(transientFiles.fileCount(), transientFiles.skippedUnsafeCount());
        receipts.save(receipt);
        PrivacyPolicy policy = current();
        if (policy.getRevision() == database.policyRevision()) {
            policy.recordSuccessfulCleanup(completedAt, policy.isAutomaticCleanupEligible()
                    ? completedAt.plus(1, ChronoUnit.DAYS) : null);
            policies.save(policy);
        }
        return CleanupReceiptView.from(receipt);
    }

    private PrivacyPolicy current() {
        return policies.findById(PrivacyPolicy.SINGLETON_ID).orElseGet(() -> policies.save(new PrivacyPolicy()));
    }

    private CleanupPreview preview(PrivacyPolicy policy, AssistanceContextMode mode, Instant cutoff,
            Instant transientCutoff,
            PreviewBasis previewBasis) {
        Instant now = clock.instant();
        var transientPreview = transientRetention.preview(transientCutoff, now.minus(90, ChronoUnit.DAYS), now);
        return new CleanupPreview(previewBasis, PrivacyCleanupReceipt.RETENTION_ENFORCEMENT,
                policy.getRevision(), mode, cutoff,
                Math.toIntExact(assistanceCleanup.countRunsBefore(cutoff)),
                Math.toIntExact(assistanceCleanup.countDecisionsForRunsBefore(cutoff)), transientCutoff,
                transientPreview.databaseRecordCount(), transientPreview.auditMetadataRecordCount(),
                transientPreview.files().fileCount(),
                transientPreview.files().skippedUnsafeCount());
    }

    private Instant cutoff(PrivacyPolicy policy, Instant now) {
        return cutoff(policy.getAssistanceContextMode(), policy.getDerivedContextRetentionDays(), now);
    }

    private Instant cutoff(AssistanceContextMode mode, Integer retentionDays, Instant now) {
        return mode == AssistanceContextMode.TIME_BOUND ? now.minus(retentionDays, ChronoUnit.DAYS) : now;
    }

    private Instant transientCutoff(PrivacyPolicy policy, Instant now) {
        return now.minus(policy.getTransientIngestionRetentionDays(), ChronoUnit.DAYS);
    }

    public record UpdatePolicy(AssistanceContextMode assistanceContextMode, Integer derivedContextRetentionDays,
            Integer transientIngestionRetentionDays, boolean connectedAssistanceEnabled,
            String consentTextVersion, boolean consentAccepted) {
        public UpdatePolicy(AssistanceContextMode assistanceContextMode, Integer derivedContextRetentionDays,
                boolean connectedAssistanceEnabled, String consentTextVersion, boolean consentAccepted) {
            this(assistanceContextMode, derivedContextRetentionDays, null, connectedAssistanceEnabled,
                    consentTextVersion, consentAccepted);
        }
    }

    public record PolicyView(long revision, AssistanceContextMode assistanceContextMode,
            Integer derivedContextRetentionDays, int transientIngestionRetentionDays,
            boolean connectedAssistanceEnabled, String consentTextVersion,
            String currentNoticeVersion, Instant consentAcceptedAt, Instant lastSuccessfulCleanupAt, Instant nextScheduledCleanupAt,
            Instant createdAt, Instant updatedAt) {
        static PolicyView from(PrivacyPolicy value) {
            return new PolicyView(value.getRevision(), value.getAssistanceContextMode(),
                    value.getDerivedContextRetentionDays(), value.getTransientIngestionRetentionDays(),
                    value.isConnectedAssistanceEnabled(),
                    value.getConsentTextVersion(), CURRENT_NOTICE_VERSION, value.getConsentAcceptedAt(), value.getLastSuccessfulCleanupAt(),
                    value.getNextScheduledCleanupAt(), value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    public enum PreviewBasis { CURRENT_POLICY, PROPOSED_POLICY }

    public record AssistanceContextModeChanged(AssistanceContextMode previousMode,
            AssistanceContextMode currentMode) {}

    private record DatabaseCleanupPhase(UUID receiptId, long policyRevision, Instant transientCutoff) {}

    public record CleanupPreview(PreviewBasis previewBasis, String category, long policyRevision,
            AssistanceContextMode assistanceContextMode, Instant cutoff, int assistanceRunCount,
            int assistanceDecisionCount, Instant transientCutoff, int transientDatabaseRecordCount,
            int auditMetadataRecordCount, int transientFileCount, int skippedUnsafeFileCount) {
        public int totalRecords() {
            return assistanceRunCount + assistanceDecisionCount + transientDatabaseRecordCount
                    + auditMetadataRecordCount + transientFileCount;
        }
    }

    public record CleanupReceiptView(java.util.UUID id, long policyRevision, String category, Instant cutoff,
            int assistanceRunCount, int assistanceDecisionCount, Instant transientCutoff,
            int transientDatabaseRecordCount, int auditMetadataRecordCount,
            int transientFileCount, int skippedUnsafeFileCount,
            String outcome, Instant createdAt) {
        static CleanupReceiptView from(PrivacyCleanupReceipt value) {
            return new CleanupReceiptView(value.getId(), value.getPolicyRevision(), value.getCategory(),
                    value.getCutoff(), value.getAssistanceRunCount(), value.getAssistanceDecisionCount(),
                    value.getTransientCutoff(), value.getTransientDatabaseRecordCount(),
                    value.getAuditMetadataRecordCount(), value.getTransientFileCount(),
                    value.getSkippedUnsafeFileCount(), value.getOutcome(),
                    value.getCreatedAt());
        }
    }
}
