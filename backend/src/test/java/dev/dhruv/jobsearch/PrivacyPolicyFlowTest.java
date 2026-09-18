package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.dhruv.jobsearch.profile.LocalUserProfile;
import dev.dhruv.jobsearch.profile.LocalUserProfileService;

@SpringBootTest
class PrivacyPolicyFlowTest {

    @TempDir Path temporary;

    @Autowired PrivacyPolicyService privacy;
    @Autowired LocalUserProfileService profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PrivacyPolicyController controller;
    @Autowired PrivacyPolicyRepository policyRepository;
    @Autowired PrivacyCleanupReceiptRepository receiptRepository;
    @Autowired PrivacyAssistanceCleanupRepository cleanupRepository;
    @Autowired TransientRetentionService transientRetention;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearPrivacyFixture() {
        jdbc.update("delete from assistance_decision");
        jdbc.update("delete from assistance_run");
        jdbc.update("delete from privacy_cleanup_receipt");
        jdbc.update("delete from privacy_policy");
    }

    @Test
    void persistsValidatedModesAndExposesTheControllerContract() {
        var initial = controller.get();
        assertThat(initial.revision()).isEqualTo(1);
        assertThat(initial.assistanceContextMode()).isEqualTo(AssistanceContextMode.STATELESS);
        assertThat(initial.derivedContextRetentionDays()).isNull();
        assertThat(initial.transientIngestionRetentionDays()).isEqualTo(7);
        assertThat(initial.connectedAssistanceEnabled()).isFalse();

        var sessionOnly = controller.update(new PrivacyPolicyController.UpdatePolicyRequest(
                AssistanceContextMode.SESSION_ONLY, null, 30, false, "v1", true));
        assertThat(sessionOnly.revision()).isEqualTo(2);
        assertThat(sessionOnly.assistanceContextMode()).isEqualTo(AssistanceContextMode.SESSION_ONLY);
        assertThat(sessionOnly.derivedContextRetentionDays()).isNull();
        assertThat(sessionOnly.transientIngestionRetentionDays()).isEqualTo(30);

        var updated = controller.update(new PrivacyPolicyController.UpdatePolicyRequest(
                AssistanceContextMode.TIME_BOUND, 30, true, "v1", true));
        assertThat(updated.revision()).isEqualTo(3);
        assertThat(updated.assistanceContextMode()).isEqualTo(AssistanceContextMode.TIME_BOUND);
        assertThat(updated.derivedContextRetentionDays()).isEqualTo(30);
        assertThat(updated.transientIngestionRetentionDays()).isEqualTo(30);
        assertThat(updated.connectedAssistanceEnabled()).isTrue();
        assertThat(updated.consentTextVersion()).isEqualTo("v1");
        assertThat(updated.currentNoticeVersion()).isEqualTo("v1");
        assertThat(updated.consentAcceptedAt()).isNotNull();

        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.TIME_BOUND, 14, false, "v1", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7, 30, or 90");
        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.STATELESS, 7, false, "v1", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only to time-bound");
        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.STATELESS, null, 14, false, "v1", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 or 30");
        var connectedSession = privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.SESSION_ONLY, null, true, "v1", true));
        assertThat(connectedSession.connectedAssistanceEnabled()).isTrue();
        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.TIME_BOUND, 7, true, null, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accept the current privacy notice");
        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.STATELESS, null, false, null, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accept the current privacy notice");
        assertThatThrownBy(() -> privacy.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.TIME_BOUND, 7, false, "invented-version", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current privacy notice version");
    }

    @Test
    void cleanupDeletesOnlyExpiredAssistanceContextAndLeavesExplicitRecordsUntouched() {
        profiles.save(new LocalUserProfile.ProfileValues("Privacy Test User", "Staff Engineer", null, null, null,
                null, null, null, null, null, null, null, null, null, true));
        UUID expiredRun = insertRun(Instant.now().minus(8, ChronoUnit.DAYS));
        insertDecision(expiredRun, Instant.now().minus(8, ChronoUnit.DAYS));
        UUID currentRun = insertRun(Instant.now());

        privacy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.TIME_BOUND, 7,
                false, "v1", true));

        var preview = controller.cleanupPreview();
        assertThat(preview.category()).isEqualTo("RETENTION_ENFORCEMENT");
        assertThat(preview.assistanceRunCount()).isOne();
        assertThat(preview.assistanceDecisionCount()).isOne();
        assertThat(preview.totalRecords()).isEqualTo(2);

        assertThatThrownBy(() -> controller.cleanup("missing-confirmation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confirm this derived-context cleanup");
        var receipt = controller.cleanup("run-derived-cleanup");
        assertThat(receipt.outcome()).isEqualTo("SUCCESS");
        assertThat(receipt.assistanceRunCount()).isOne();
        assertThat(receipt.assistanceDecisionCount()).isOne();

        assertThat(count("assistance_run", expiredRun)).isZero();
        assertThat(count("assistance_run", currentRun)).isOne();
        assertThat(profiles.get().getDisplayName()).isEqualTo("Privacy Test User");
        assertThat(jdbc.queryForObject("select count(*) from privacy_cleanup_receipt", Integer.class)).isEqualTo(1);
        assertThat(privacy.get().lastSuccessfulCleanupAt()).isNotNull();
        assertThat(privacy.get().nextScheduledCleanupAt())
                .isEqualTo(privacy.get().lastSuccessfulCleanupAt().plus(1, ChronoUnit.DAYS));

        var replay = privacy.cleanup();
        assertThat(replay.assistanceRunCount()).isZero();
        assertThat(replay.assistanceDecisionCount()).isZero();
        assertThat(count("assistance_run", currentRun)).isOne();
    }

    @ParameterizedTest
    @EnumSource(value = AssistanceContextMode.class, names = { "STATELESS", "SESSION_ONLY" })
    void nonDurableModesRemoveAllPersistedAssistanceContextOnManualCleanup(AssistanceContextMode mode) {
        UUID run = insertRun(Instant.now().minusSeconds(1));
        insertDecision(run, Instant.now().minusSeconds(1));
        privacy.update(new PrivacyPolicyService.UpdatePolicy(mode, null,
                false, "v1", true));

        var receipt = privacy.cleanup();

        assertThat(receipt.assistanceRunCount()).isOne();
        assertThat(receipt.assistanceDecisionCount()).isOne();
        assertThat(count("assistance_run", run)).isZero();
    }

    @Test
    void previewsAShorterProposedPolicyWithoutMutatingTheAcceptedPolicy() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        PrivacyPolicyService fixed = serviceAt(now);
        fixed.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.TIME_BOUND, 90,
                false, "v1", true));
        insertRun(now.minus(10, ChronoUnit.DAYS));

        var current = fixed.previewCleanup();
        var proposed = new PrivacyPolicyController(fixed, null, null).proposedCleanupPreview(
                new PrivacyPolicyController.ProposedCleanupPreviewRequest(AssistanceContextMode.TIME_BOUND, 7));
        var stillStored = fixed.get();

        assertThat(current.previewBasis()).isEqualTo(PrivacyPolicyService.PreviewBasis.CURRENT_POLICY);
        assertThat(current.assistanceRunCount()).isZero();
        assertThat(proposed.previewBasis()).isEqualTo(PrivacyPolicyService.PreviewBasis.PROPOSED_POLICY);
        assertThat(proposed.assistanceContextMode()).isEqualTo(AssistanceContextMode.TIME_BOUND);
        assertThat(proposed.assistanceRunCount()).isOne();
        assertThat(stillStored.derivedContextRetentionDays()).isEqualTo(90);
        assertThat(stillStored.revision()).isEqualTo(proposed.policyRevision());
    }

    @Test
    void schedulerRunsAcceptedPoliciesInEveryModeAndTracksTheNextDailyDueTime() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        UUID unacceptedRun = insertRun(now.minus(10, ChronoUnit.DAYS));
        PrivacyPolicyService fixed = serviceAt(now);
        PrivacyCleanupScheduler scheduler = new PrivacyCleanupScheduler(fixed);

        runInTransaction(scheduler::runStartupCleanup);
        assertThat(count("assistance_run", unacceptedRun)).isOne();
        assertThat(receiptRepository.count()).isZero();

        fixed.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.STATELESS, null,
                false, "v1", true));
        runInTransaction(scheduler::runStartupCleanup);
        assertThat(count("assistance_run", unacceptedRun)).isZero();
        assertThat(receiptRepository.count()).isOne();

        UUID expiredRun = insertRun(now.minus(10, ChronoUnit.DAYS));
        fixed.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.TIME_BOUND, 7,
                false, "v1", true));
        assertThat(fixed.get().nextScheduledCleanupAt()).isEqualTo(now.plus(1, ChronoUnit.DAYS));
        runInTransaction(scheduler::runStartupCleanup);
        assertThat(count("assistance_run", expiredRun)).isZero();
        assertThat(receiptRepository.count()).isEqualTo(2);
        assertThat(fixed.get().lastSuccessfulCleanupAt()).isEqualTo(now);
        assertThat(fixed.get().nextScheduledCleanupAt()).isEqualTo(now.plus(1, ChronoUnit.DAYS));

        runInTransaction(scheduler::runDueDailyCleanup);
        assertThat(receiptRepository.count()).isEqualTo(2);

        Instant nextDay = now.plus(1, ChronoUnit.DAYS);
        PrivacyPolicyService nextDayService = serviceAt(nextDay);
        PrivacyCleanupScheduler nextDayScheduler = new PrivacyCleanupScheduler(nextDayService);
        runInTransaction(nextDayScheduler::runDueDailyCleanup);
        assertThat(receiptRepository.count()).isEqualTo(3);
        assertThat(nextDayService.get().lastSuccessfulCleanupAt()).isEqualTo(nextDay);
        assertThat(nextDayService.get().nextScheduledCleanupAt()).isEqualTo(nextDay.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void repeatedSettingsSavesPreserveNoticeAcceptanceAndTheExistingCleanupDueTime() {
        Instant acceptedAt = Instant.parse("2026-09-18T08:00:00Z");
        PrivacyPolicyService initial = serviceAt(acceptedAt);
        var accepted = initial.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.TIME_BOUND, 30, false, "v1", true));

        Instant later = acceptedAt.plus(12, ChronoUnit.HOURS);
        PrivacyPolicyService repeated = serviceAt(later);
        var savedAgain = repeated.update(new PrivacyPolicyService.UpdatePolicy(
                AssistanceContextMode.TIME_BOUND, 30, false, "v1", true));

        assertThat(savedAgain.consentAcceptedAt()).isEqualTo(accepted.consentAcceptedAt());
        assertThat(savedAgain.nextScheduledCleanupAt()).isEqualTo(accepted.nextScheduledCleanupAt());
        assertThat(savedAgain.nextScheduledCleanupAt()).isEqualTo(acceptedAt.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void commitsDatabaseCleanupAndPartialReceiptBeforeAttemptingIrreversibleFileDeletion() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        UUID expiredRun = insertRun(now.minus(10, ChronoUnit.DAYS));
        TransientRetentionService interruptedFiles = new TransientRetentionService(jdbc,
                temporary.toString(), temporary.resolve("daily-high-fit-job-roles").toString()) {
            @Override FileDeletion deleteFiles(Instant cutoff) {
                throw new IllegalStateException("simulated filesystem interruption");
            }
        };
        PrivacyPolicyService fixed = new PrivacyPolicyService(policyRepository, receiptRepository,
                cleanupRepository, interruptedFiles, transactionManager, Clock.fixed(now, ZoneOffset.UTC));
        fixed.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.STATELESS, null,
                false, "v1", true));

        assertThatThrownBy(fixed::cleanup).hasMessageContaining("simulated filesystem interruption");

        assertThat(count("assistance_run", expiredRun)).isZero();
        assertThat(jdbc.queryForObject("select outcome from privacy_cleanup_receipt", String.class))
                .isEqualTo(PrivacyCleanupReceipt.PARTIAL);
        assertThat(fixed.get().lastSuccessfulCleanupAt()).isNull();
    }

    private PrivacyPolicyService serviceAt(Instant instant) {
        return new PrivacyPolicyService(policyRepository, receiptRepository, cleanupRepository,
                transientRetention, transactionManager, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void runInTransaction(Runnable runnable) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> runnable.run());
    }

    private UUID insertRun(Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into assistance_run
                    (id, use_case, target_id, input_hash, provider, model, prompt_version, schema_version,
                     status, result_payload, created_at, completed_at)
                values (?, 'WEEKLY_REFLECTION', ?, ?, 'fixture', 'fixture', 'fixture', 'fixture',
                        'COMPLETED', '{}', ?, ?)
                """, id, UUID.randomUUID(), UUID.randomUUID().toString().replace("-", ""),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return id;
    }

    private void insertDecision(UUID runId, Instant createdAt) {
        jdbc.update("""
                insert into assistance_decision (id, run_id, decision_type, note, created_at)
                values (?, ?, 'DISMISSED', 'fixture', ?)
                """, UUID.randomUUID(), runId, Timestamp.from(createdAt));
    }

    private int count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
    }
}
