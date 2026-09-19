package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransientRetentionServiceTest {

    @Autowired JdbcTemplate jdbc;
    @TempDir Path temporary;

    @Test
    void bindsRetentionCutoffsAsTimestampWithTimeZone() {
        Instant cutoff = Instant.parse("2026-09-11T08:00:00Z");

        var parameter = TransientRetentionService.timestampWithTimeZone(cutoff);

        assertThat(parameter.getSqlType()).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(parameter.getValue()).isEqualTo(OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }

    @Test
    void deletesExpiredImportProvenanceButPreservesSavedConnectionsAndBoundaryRows() {
        Instant cutoff = Instant.parse("2026-09-11T08:00:00Z");
        UUID expiredBatch = UUID.randomUUID();
        UUID boundaryBatch = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        UUID opportunity = UUID.randomUUID();
        UUID skill = UUID.randomUUID();
        UUID orphanSnapshot = UUID.randomUUID();
        UUID evidenceSnapshot = UUID.randomUUID();
        UUID observation = UUID.randomUUID();
        insertLinkedInBatch(expiredBatch, cutoff.minusSeconds(1));
        insertLinkedInBatch(boundaryBatch, cutoff);
        jdbc.update("""
                insert into linkedin_connection
                    (id, full_name, normalized_profile_url, source_row, import_batch_id,
                     created_at, updated_at, version)
                values (?, 'Saved Contact', ?, 1, ?, ?, ?, 0)
                """, connection, "https://linkedin.example/" + connection, expiredBatch,
                Timestamp.from(cutoff.minusSeconds(1)), Timestamp.from(cutoff.minusSeconds(1)));
        insertEvidenceFixture(opportunity, skill, orphanSnapshot, evidenceSnapshot, observation, cutoff.minusSeconds(1));
        TransientRetentionService service = new TransientRetentionService(jdbc,
                temporary.toString(), temporary.resolve("daily").toString());

        var deleted = service.deleteDatabase(cutoff, cutoff.minusSeconds(90L * 24 * 60 * 60), cutoff);

        assertThat(deleted.recordCount()).isGreaterThanOrEqualTo(1);
        assertThat(count("linkedin_connection_import_batch", expiredBatch)).isZero();
        assertThat(count("linkedin_connection_import_batch", boundaryBatch)).isOne();
        assertThat(count("linkedin_connection", connection)).isOne();
        assertThat(count("job_description_snapshot", orphanSnapshot)).isZero();
        assertThat(count("job_description_snapshot", evidenceSnapshot)).isOne();
        assertThat(count("job_skill_observation", observation)).isOne();
        assertThat(jdbc.queryForObject("select import_batch_id from linkedin_connection where id=?",
                UUID.class, connection)).isNull();
        assertThat(service.deleteDatabase(cutoff, cutoff.minusSeconds(90L * 24 * 60 * 60), cutoff).recordCount())
                .isZero();

        jdbc.update("delete from linkedin_connection where id=?", connection);
        jdbc.update("delete from linkedin_connection_import_batch where id=?", boundaryBatch);
        jdbc.update("delete from job_skill_observation where id=?", observation);
        jdbc.update("delete from job_description_snapshot where id=?", evidenceSnapshot);
        jdbc.update("delete from job_opportunity where id=?", opportunity);
        jdbc.update("delete from canonical_skill where id=?", skill);
    }

    @Test
    void deletesOnlyExpiredKnownDailyFilesAndFailsClosedOutsideTheWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("workspace"));
        Path imports = Files.createDirectory(workspace.resolve("daily"));
        Instant cutoff = Instant.parse("2026-09-11T08:00:00Z");
        Path expired = Files.writeString(imports.resolve("2026-09-01-high-fit-openings.xlsx"), "old");
        Path boundary = Files.writeString(imports.resolve("2026-09-11-top-three-actions.txt"), "boundary");
        Path unrelated = Files.writeString(imports.resolve("personal-notes.txt"), "keep");
        Files.setLastModifiedTime(expired, FileTime.from(cutoff.minusSeconds(1)));
        Files.setLastModifiedTime(boundary, FileTime.from(cutoff));
        Files.setLastModifiedTime(unrelated, FileTime.from(cutoff.minusSeconds(1)));
        TransientRetentionService service = new TransientRetentionService(jdbc,
                workspace.toString(), imports.toString());

        assertThat(service.preview(cutoff, cutoff.minusSeconds(90L * 24 * 60 * 60), cutoff).files().fileCount())
                .isOne();
        var deletion = service.deleteFiles(cutoff);

        assertThat(deletion.fileCount()).isOne();
        assertThat(deletion.skippedUnsafeCount()).isZero();
        assertThat(expired).doesNotExist();
        assertThat(boundary).exists();
        assertThat(unrelated).exists();
        assertThat(service.deleteFiles(cutoff).fileCount()).isZero();

        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("2026-09-01-high-fit-openings.xlsx"), "private");
        Files.setLastModifiedTime(outsideFile, FileTime.from(cutoff.minusSeconds(1)));
        var rejected = new TransientRetentionService(jdbc, workspace.toString(), outside.toString())
                .deleteFiles(cutoff);
        assertThat(rejected.skippedUnsafeCount()).isOne();
        assertThat(outsideFile).exists();
    }

    @Test
    void expiresOldAuditReceiptsButPreservesActiveOperationsAndCurrentPolicy() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        Instant old = now.minusSeconds(91L * 24 * 60 * 60);
        UUID expiredPreview = UUID.randomUUID();
        UUID activePreview = UUID.randomUUID();
        UUID boundaryPreview = UUID.randomUUID();
        UUID currentPreview = UUID.randomUUID();
        UUID transmissionReceipt = UUID.randomUUID();
        UUID newerChildReceipt = UUID.randomUUID();
        UUID boundaryReceipt = UUID.randomUUID();
        UUID currentReceipt = UUID.randomUUID();
        UUID completedLifecycle = UUID.randomUUID();
        UUID activeLifecycle = UUID.randomUUID();
        UUID cleanupReceipt = UUID.randomUUID();
        insertTransmissionPreview(expiredPreview, old, old.plusSeconds(300), old.plusSeconds(10));
        insertTransmissionPreview(activePreview, old, now.plusSeconds(300), null);
        Instant auditCutoff = now.minusSeconds(90L * 24 * 60 * 60);
        insertTransmissionPreview(boundaryPreview, auditCutoff, old.plusSeconds(300), old.plusSeconds(10));
        insertTransmissionPreview(currentPreview, now.minusSeconds(24 * 60 * 60),
                now.minusSeconds(23 * 60 * 60), now.minusSeconds(23 * 60 * 60));
        insertTransmissionReceipt(transmissionReceipt, expiredPreview, old);
        // A child can be newer than its preview after a delayed operation; deleting the old parent must remain FK-safe.
        insertTransmissionReceipt(newerChildReceipt, expiredPreview, now.minusSeconds(24 * 60 * 60));
        insertTransmissionReceipt(boundaryReceipt, boundaryPreview, auditCutoff);
        insertTransmissionReceipt(currentReceipt, currentPreview, now.minusSeconds(24 * 60 * 60));
        insertLifecycle(completedLifecycle, "COMPLETED", old, old);
        insertLifecycle(activeLifecycle, "DATABASE_DELETED", old, null);
        jdbc.update("""
                insert into privacy_cleanup_receipt
                    (id, policy_revision, category, cutoff, assistance_run_count, assistance_decision_count,
                     transient_database_record_count, audit_metadata_record_count, transient_file_count,
                     skipped_unsafe_file_count, outcome, created_at)
                values (?, 1, 'RETENTION_ENFORCEMENT', ?, 0, 0, 0, 0, 0, 0, 'SUCCESS', ?)
                """, cleanupReceipt, Timestamp.from(old), Timestamp.from(old));
        TransientRetentionService service = new TransientRetentionService(jdbc,
                temporary.toString(), temporary.resolve("daily").toString());
        int policyCount = jdbc.queryForObject("select count(*) from privacy_policy", Integer.class);

        var result = service.deleteDatabase(now.minusSeconds(7L * 24 * 60 * 60),
                auditCutoff, now);

        assertThat(result.auditMetadataRecordCount()).isGreaterThanOrEqualTo(4);
        assertThat(count("transmission_receipt", transmissionReceipt)).isZero();
        assertThat(count("transmission_receipt", newerChildReceipt)).isZero();
        assertThat(count("transmission_preview", expiredPreview)).isZero();
        assertThat(count("data_lifecycle_operation", completedLifecycle)).isZero();
        assertThat(count("privacy_cleanup_receipt", cleanupReceipt)).isZero();
        assertThat(count("transmission_preview", activePreview)).isOne();
        assertThat(count("transmission_preview", boundaryPreview)).isOne();
        assertThat(count("transmission_receipt", boundaryReceipt)).isOne();
        assertThat(count("transmission_preview", currentPreview)).isOne();
        assertThat(count("transmission_receipt", currentReceipt)).isOne();
        assertThat(count("data_lifecycle_operation", activeLifecycle)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from privacy_policy", Integer.class)).isEqualTo(policyCount);

        jdbc.update("delete from transmission_receipt where id in (?, ?)", boundaryReceipt, currentReceipt);
        jdbc.update("delete from transmission_preview where id in (?, ?, ?)", activePreview, boundaryPreview, currentPreview);
        jdbc.update("delete from data_lifecycle_operation where id=?", activeLifecycle);
    }

    private void insertLinkedInBatch(UUID id, Instant startedAt) {
        jdbc.update("""
                insert into linkedin_connection_import_batch
                    (id, source_file, content_hash, status, rows_seen, connections_created,
                     connections_updated, rows_skipped, started_at)
                values (?, 'fixture.csv', ?, 'COMPLETED', 0, 0, 0, 0, ?)
                """, id, id.toString().replace("-", "") + id.toString().replace("-", ""),
                Timestamp.from(startedAt));
    }

    private void insertEvidenceFixture(UUID opportunity, UUID skill, UUID orphanSnapshot, UUID evidenceSnapshot,
            UUID observation, Instant createdAt) {
        Timestamp time = Timestamp.from(createdAt);
        jdbc.update("""
                insert into job_opportunity
                    (id, company_name, role_title, status, is_demo, discovered_at, created_at, updated_at, version)
values (?, 'Retention Test', 'Engineer', 'NEW', false, ?, ?, ?, 0)
                """, opportunity, time, time, time);
        jdbc.update("""
                insert into canonical_skill
                    (id, name, normalized_name, category, active, created_at, updated_at, version)
                values (?, ?, ?, 'BACKEND', true, ?, ?, 0)
                """, skill, "Retention Skill " + skill, "retention-skill-" + skill, time, time);
        jdbc.update("""
                insert into job_description_snapshot
                    (id, opportunity_id, source_type, content, content_hash, captured_at)
                values (?, ?, 'PASTED_DESCRIPTION', 'orphan', ?, ?),
                       (?, ?, 'PASTED_DESCRIPTION', 'evidence', ?, ?)
                """, orphanSnapshot, opportunity, hex(orphanSnapshot), time,
                evidenceSnapshot, opportunity, hex(evidenceSnapshot), time);
        jdbc.update("""
                insert into job_skill_observation
                    (id, opportunity_id, snapshot_id, skill_id, strength, evidence_snippet,
                     evidence_fingerprint, extraction_method, review_status, observed_at,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, 'REQUIRED', 'Evidence', ?, 'MANUAL', 'ACCEPTED', ?, ?, ?, 0)
                """, observation, opportunity, evidenceSnapshot, skill, hex(observation), time, time, time);
    }

    private void insertTransmissionPreview(UUID id, Instant createdAt, Instant expiresAt, Instant consumedAt) {
        jdbc.update("""
                insert into transmission_preview
                    (id, token_hash, operation, destination, purpose, minimized_fields, payload_hash,
                     expires_at, consumed_at, created_at)
                values (?, ?, 'OPENAI_INBOX_STRUCTURING', 'https://example.test', 'test', 'field', ?, ?, ?, ?)
                """, id, hex(id), hex(UUID.randomUUID()), Timestamp.from(expiresAt),
                consumedAt == null ? null : Timestamp.from(consumedAt), Timestamp.from(createdAt));
    }

    private void insertTransmissionReceipt(UUID id, UUID previewId, Instant createdAt) {
        jdbc.update("""
                insert into transmission_receipt
                    (id, preview_id, operation, destination, purpose, minimized_fields, payload_hash,
                     outcome, created_at, completed_at)
                values (?, ?, 'OPENAI_INBOX_STRUCTURING', 'https://example.test', 'test', 'field', ?,
                        'SUCCESS', ?, ?)
                """, id, previewId, hex(id), Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertLifecycle(UUID id, String status, Instant createdAt, Instant completedAt) {
        jdbc.update("""
                insert into data_lifecycle_operation
                    (id, operation_type, category_scope, status, plan_digest, confirmation_token_hash,
                     planned_database_rows, planned_file_count, planned_byte_count, skipped_unsafe_entry_count,
                     created_at, expires_at, completed_at)
                values (?, 'EXPORT', 'EXPLICIT_USER_RECORDS', ?, ?, ?, 0, 0, 0, 0, ?, ?, ?)
                """, id, status, hex(id), hex(UUID.randomUUID()), Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(300)),
                completedAt == null ? null : Timestamp.from(completedAt));
    }

    private String hex(UUID value) {
        return value.toString().replace("-", "") + value.toString().replace("-", "");
    }

    private int count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id);
    }
}
