package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.Category;
import dev.dhruv.jobsearch.privacy.DataLifecycleOperation.OperationStatus;
import dev.dhruv.jobsearch.privacy.DataLifecycleOperation.OperationType;

@SpringBootTest
class DataLifecycleDeletionFlowTest {

    private static final Path WORKSPACE = temporaryDirectory();

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("app.privacy.workspace-root", WORKSPACE::toString);
        registry.add("app.application-artifacts.folder", () -> WORKSPACE.resolve("application-resumes").toString());
        registry.add("app.notes.folder", () -> WORKSPACE.resolve("notes").toString());
        registry.add("app.imports.daily-high-fit.folder", () -> WORKSPACE.resolve("daily-high-fit-job-roles").toString());
        registry.add("app.imports.linkedin-connections.file",
                () -> WORKSPACE.resolve("linkedin-data-import/Connections.csv").toString());
        registry.add("app.privacy.preparation-workspace-folder",
                () -> WORKSPACE.resolve("preparation-workspace").toString());
        registry.add("app.cohorts.company-targets.folder", () -> WORKSPACE.resolve("company-targets").toString());
    }

    @BeforeAll
    static void files() throws Exception {
        for (String folder : new String[] { "application-resumes", "notes", "daily-high-fit-job-roles",
                "linkedin-data-import", "preparation-workspace", "company-targets" }) {
            Files.createDirectories(WORKSPACE.resolve(folder));
        }
    }

    @Autowired DataLifecycleService lifecycle;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejectsStaleScopeThenDeletesFkGraphAndFilesIdempotentlyWhilePreservingCatalogsAndJournal() throws Exception {
        verifyTransientDeletionPreservesAcceptedSkillEvidence();
        seedApplicationGraph();
        Path resume = Files.writeString(WORKSPACE.resolve("application-resumes/resume.pdf"), "private resume");
        Path note = Files.writeString(WORKSPACE.resolve("notes/interview.txt"), "private note");
        long referenceRows = count("canonical_skill") + count("skill_alias");

        var exportPreview = lifecycle.preview(new DataLifecycleService.PreviewCommand(
                OperationType.EXPORT, EnumSet.of(Category.EXPLICIT_USER_RECORDS)));
        var export = lifecycle.export(exportPreview.operationId(), exportPreview.previewToken());
        assertThat(export.receipt().status()).isEqualTo(OperationStatus.EXPORT_COMPLETED);
        assertThat(export.archive()).isNotEmpty();
        assertThat(zipEntries(export.archive())).contains("manifest.json", "database/job_opportunity.json");

        var stalePreview = lifecycle.preview(new DataLifecycleService.PreviewCommand(
                OperationType.DELETE_CATEGORIES, EnumSet.of(Category.EXPLICIT_USER_RECORDS)));
        insertContact();
        assertThatThrownBy(() -> lifecycle.delete(stalePreview.operationId(), stalePreview.previewToken(),
                stalePreview.confirmationPhrase()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed after preview");
        assertThat(lifecycle.receipt(stalePreview.operationId()).status()).isEqualTo(OperationStatus.STALE);

        var preview = lifecycle.preview(new DataLifecycleService.PreviewCommand(
                OperationType.DELETE_ALL, EnumSet.of(Category.EXPLICIT_USER_RECORDS)));
        assertThat(preview.confirmationPhrase())
                .isEqualTo("DELETE " + preview.databaseRows() + " ROWS AND " + preview.fileCount() + " FILES");
        assertThat(preview.effectiveCategories()).contains(Category.TRANSIENT_IMPORT_DATA,
                Category.DERIVED_CONTEXT, Category.AUDIT_AND_POLICY_METADATA);
        assertThat(preview.retainedApplicationScopes()).contains("canonical_skill", "skill_alias",
                "data_lifecycle_operation");

        var receipt = lifecycle.delete(preview.operationId(), preview.previewToken(), preview.confirmationPhrase());

        assertThat(receipt.status()).isEqualTo(OperationStatus.COMPLETED);
        assertThat(receipt.affectedDatabaseRows()).isEqualTo(preview.databaseRows());
        assertThat(receipt.deletedFileCount()).isEqualTo(preview.fileCount());
        assertThat(receipt.failedFileCount()).isZero();
        assertThat(resume).doesNotExist();
        assertThat(note).doesNotExist();
        assertThat(count("job_application")).isZero();
        assertThat(count("application_event")).isZero();
        assertThat(count("job_opportunity")).isZero();
        assertThat(count("resume_variant")).isZero();
        assertThat(count("network_contact")).isZero();
        assertThat(count("canonical_skill") + count("skill_alias")).isEqualTo(referenceRows);
        assertThat(count("data_lifecycle_operation")).isGreaterThanOrEqualTo(2);

        var replay = lifecycle.delete(preview.operationId(), preview.previewToken(), preview.confirmationPhrase());
        assertThat(replay).usingRecursiveComparison()
                .ignoringFields("completedAt")
                .isEqualTo(receipt);
        assertThat(replay.completedAt()).isCloseTo(receipt.completedAt(),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
    }

    private void seedApplicationGraph() {
        UUID resume = UUID.randomUUID();
        UUID opportunity = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                insert into resume_variant
                    (id, name, target_role, version_label, active, created_at, updated_at, version)
                values (?, 'Lifecycle resume', 'Staff Engineer', 'v1', true, ?, ?, 0)
                """, resume, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into job_opportunity
                    (id, company_name, role_title, status, discovered_at, created_at, updated_at, is_demo, version)
                values (?, 'Lifecycle Co', 'Staff Engineer', 'NEW', ?, ?, ?, false, 0)
                """, opportunity, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into job_application
                    (id, opportunity_id, resume_variant_id, stage, follow_up_active, created_at, updated_at, version)
                values (?, ?, ?, 'DRAFT', false, ?, ?, 0)
                """, application, opportunity, resume, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into application_event (id, application_id, to_stage, note, occurred_at)
                values (?, ?, 'DRAFT', 'fixture', ?)
                """, UUID.randomUUID(), application, Timestamp.from(now));
    }

    private void verifyTransientDeletionPreservesAcceptedSkillEvidence() {
        UUID opportunity = UUID.randomUUID();
        UUID skill = UUID.randomUUID();
        UUID referencedSnapshot = UUID.randomUUID();
        UUID unreferencedSnapshot = UUID.randomUUID();
        UUID observation = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                insert into job_opportunity
                    (id, company_name, role_title, status, discovered_at, created_at, updated_at, is_demo, version)
                values (?, 'Evidence Co', 'Staff Engineer', 'NEW', ?, ?, ?, false, 0)
                """, opportunity, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into canonical_skill
                    (id, name, normalized_name, category, active, created_at, updated_at, version)
                values (?, 'Lifecycle Evidence Skill', ?, 'OTHER', true, ?, ?, 0)
                """, skill, "lifecycle-evidence-skill-" + skill, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into job_description_snapshot
                    (id, opportunity_id, source_type, source_label, content, content_hash, captured_at)
                values (?, ?, 'MANUAL_DESCRIPTION', 'accepted evidence', 'accepted evidence body', ?, ?)
                """, referencedSnapshot, opportunity, "a".repeat(63) + "1", Timestamp.from(now));
        jdbc.update("""
                insert into job_description_snapshot
                    (id, opportunity_id, source_type, source_label, content, content_hash, captured_at)
                values (?, ?, 'MANUAL_DESCRIPTION', 'unreferenced cache', 'unreferenced cache body', ?, ?)
                """, unreferencedSnapshot, opportunity, "b".repeat(63) + "2", Timestamp.from(now));
        jdbc.update("""
                insert into job_skill_observation
                    (id, opportunity_id, snapshot_id, skill_id, strength, evidence_snippet, evidence_fingerprint,
                     extraction_method, review_status, observed_at, reviewed_at, created_at, updated_at, version)
                values (?, ?, ?, ?, 'REQUIRED', 'accepted evidence', ?, 'MANUAL', 'ACCEPTED', ?, ?, ?, ?, 0)
                """, observation, opportunity, referencedSnapshot, skill, "c".repeat(64), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        var preview = lifecycle.preview(new DataLifecycleService.PreviewCommand(
                OperationType.DELETE_CATEGORIES, EnumSet.of(Category.TRANSIENT_IMPORT_DATA)));
        var snapshotScope = preview.scopes().stream()
                .filter(scope -> scope.name().equals("job_description_snapshot")).findFirst().orElseThrow();
        assertThat(snapshotScope.recordCount()).isOne();
        lifecycle.delete(preview.operationId(), preview.previewToken(), preview.confirmationPhrase());

        assertThat(count("job_skill_observation", observation)).isOne();
        assertThat(count("job_description_snapshot", referencedSnapshot)).isOne();
        assertThat(count("job_description_snapshot", unreferencedSnapshot)).isZero();
    }

    private void insertContact() {
        Instant now = Instant.now();
        jdbc.update("""
                insert into network_contact
                    (id, full_name, relationship_strength, created_at, updated_at, version)
                values (?, 'Lifecycle Contact', 'COLD', ?, ?, 0)
                """, UUID.randomUUID(), Timestamp.from(now), Timestamp.from(now));
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private long count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id = ?", Long.class, id);
    }

    private Set<String> zipEntries(byte[] archive) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) names.add(entry.getName());
        }
        return names;
    }

    private static Path temporaryDirectory() {
        try { return Files.createTempDirectory("lifecycle-delete-flow").toAbsolutePath().normalize(); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
}
