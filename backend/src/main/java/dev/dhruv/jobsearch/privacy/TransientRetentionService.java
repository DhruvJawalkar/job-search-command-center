package dev.dhruv.jobsearch.privacy;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;

/** Enforces the approved retention window for import staging, never saved domain records. */
@Service
class TransientRetentionService {

    private static final Pattern DAILY_IMPORT_FILE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}-(?:high-fit-openings\\.xlsx|top-three-actions\\.txt)");

    private final JdbcTemplate jdbc;
    private final Path workspaceRoot;
    private final Path dailyImports;

    TransientRetentionService(JdbcTemplate jdbc,
            @Value("${app.privacy.workspace-root}") String workspaceRoot,
            @Value("${app.imports.daily-high-fit.folder}") String dailyImports) {
        this.jdbc = jdbc;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.dailyImports = Path.of(dailyImports).toAbsolutePath().normalize();
    }

    Preview preview(Instant cutoff, Instant auditCutoff, Instant now) {
        return new Preview(countDatabase(cutoff), countAudit(auditCutoff, now), inspectFiles(cutoff, false));
    }

    DatabaseDeletion deleteDatabase(Instant cutoff, Instant auditCutoff, Instant now) {
        int matches = update("delete from inbox_duplicate_match where candidate_id in ("
                + "select c.id from generic_inbox_candidate c join generic_inbox_item i on i.id=c.inbox_item_id "
                + "where i.created_at < ?)", cutoff);
        int candidates = update("delete from generic_inbox_candidate where inbox_item_id in ("
                + "select id from generic_inbox_item where created_at < ?)", cutoff);
        int items = update("delete from generic_inbox_item where created_at < ?", cutoff);
        int observations = update("delete from opportunity_observation where created_at < ?", cutoff);
        int unreferencedSnapshots = update("delete from job_description_snapshot s where s.captured_at < ? "
                + "and not exists (select 1 from job_skill_observation o where o.snapshot_id=s.id)", cutoff);

        // These relationships point from deliberately saved records to import provenance.
        // Detach provenance first; the saved actions and connections remain untouched.
        update("update daily_priority_action set import_batch_id=null where import_batch_id in ("
                + "select id from import_batch where started_at < ?)", cutoff);
        int dailyBatches = update("delete from import_batch where started_at < ?", cutoff);
        update("update linkedin_connection set import_batch_id=null where import_batch_id in ("
                + "select id from linkedin_connection_import_batch where started_at < ?)", cutoff);
        int linkedInBatches = update("delete from linkedin_connection_import_batch where started_at < ?", cutoff);

        int transmissionReceipts = update("delete from transmission_receipt r where r.created_at < ? "
                + "or exists (select 1 from transmission_preview p where p.id=r.preview_id and p.created_at < ? "
                + "and (p.consumed_at is not null or p.expires_at < ?))", auditCutoff, auditCutoff, now);
        int transmissionPreviews = update("delete from transmission_preview where created_at < ? "
                + "and (consumed_at is not null or expires_at < ?)", auditCutoff, now);
        int lifecycleOperations = update("delete from data_lifecycle_operation where completed_at < ?", auditCutoff);
        int cleanupReceipts = update("delete from privacy_cleanup_receipt where created_at < ?", auditCutoff);
        return new DatabaseDeletion(matches + candidates + items + observations + unreferencedSnapshots
                + dailyBatches + linkedInBatches,
                transmissionReceipts + transmissionPreviews + lifecycleOperations + cleanupReceipts);
    }

    FileDeletion deleteFiles(Instant cutoff) { return inspectFiles(cutoff, true); }

    private int countDatabase(Instant cutoff) {
        List<String> queries = List.of(
                "select count(*) from inbox_duplicate_match where candidate_id in (select c.id from generic_inbox_candidate c join generic_inbox_item i on i.id=c.inbox_item_id where i.created_at < ?)",
                "select count(*) from generic_inbox_candidate where inbox_item_id in (select id from generic_inbox_item where created_at < ?)",
                "select count(*) from generic_inbox_item where created_at < ?",
                "select count(*) from opportunity_observation where created_at < ?",
                "select count(*) from job_description_snapshot s where s.captured_at < ? and not exists (select 1 from job_skill_observation o where o.snapshot_id=s.id)",
                "select count(*) from import_batch where started_at < ?",
                "select count(*) from linkedin_connection_import_batch where started_at < ?");
        return queries.stream().mapToInt(query -> queryCount(query, cutoff)).sum();
    }

    private int countAudit(Instant cutoff, Instant now) {
        int transmissionReceipts = queryCount(
                "select count(*) from transmission_receipt r where r.created_at < ? "
                        + "or exists (select 1 from transmission_preview p where p.id=r.preview_id and p.created_at < ? "
                        + "and (p.consumed_at is not null or p.expires_at < ?))",
                cutoff, cutoff, now);
        int transmissionPreviews = queryCount(
                "select count(*) from transmission_preview where created_at < ? and (consumed_at is not null or expires_at < ?)",
                cutoff, now);
        int lifecycleOperations = queryCount(
                "select count(*) from data_lifecycle_operation where completed_at < ?", cutoff);
        int cleanupReceipts = queryCount(
                "select count(*) from privacy_cleanup_receipt where created_at < ?", cutoff);
        return transmissionReceipts + transmissionPreviews + lifecycleOperations + cleanupReceipts;
    }

    private int queryCount(String sql, Instant... values) {
        return jdbc.queryForObject(sql, Integer.class, timestampParameters(values));
    }

    private int update(String sql, Instant... values) {
        return jdbc.update(sql, timestampParameters(values));
    }

    private static Object[] timestampParameters(Instant... values) {
        return Arrays.stream(values).map(TransientRetentionService::timestampWithTimeZone).toArray();
    }

    static SqlParameterValue timestampWithTimeZone(Instant value) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    private FileDeletion inspectFiles(Instant cutoff, boolean delete) {
        Counter counter = new Counter(cutoff, delete);
        try {
            if (!Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS)) return new FileDeletion(0, 0);
            if (Files.isSymbolicLink(workspaceRoot)) return new FileDeletion(0, 1);
            Path canonicalWorkspace = workspaceRoot.toRealPath();
            if (!dailyImports.startsWith(workspaceRoot)) return new FileDeletion(0, 1);
            if (!Files.exists(dailyImports, LinkOption.NOFOLLOW_LINKS)) {
                Path parent = nearestExistingParent(dailyImports);
                if (parent == null || Files.isSymbolicLink(parent)
                        || !parent.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(canonicalWorkspace)) {
                    return new FileDeletion(0, 1);
                }
                return new FileDeletion(0, 0);
            }
            if (Files.isSymbolicLink(dailyImports)) return new FileDeletion(0, 1);
            Path canonicalImports = dailyImports.toRealPath();
            if (!canonicalImports.startsWith(canonicalWorkspace)) return new FileDeletion(0, 1);
            counter.workspace = canonicalWorkspace;
            Files.walkFileTree(canonicalImports, counter);
            return new FileDeletion(counter.affected, counter.skippedUnsafe);
        } catch (IOException | SecurityException exception) {
            return new FileDeletion(counter.affected, counter.skippedUnsafe + 1);
        }
    }

    private static Path nearestExistingParent(Path path) {
        Path current = path.getParent();
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) current = current.getParent();
        return current;
    }

    record Preview(int databaseRecordCount, int auditMetadataRecordCount, FileDeletion files) {}
    record DatabaseDeletion(int recordCount, int auditMetadataRecordCount) {}
    record FileDeletion(int fileCount, int skippedUnsafeCount) {}

    private static final class Counter extends SimpleFileVisitor<Path> {
        private final Instant cutoff;
        private final boolean delete;
        private Path workspace;
        private int affected;
        private int skippedUnsafe;

        private Counter(Instant cutoff, boolean delete) { this.cutoff = cutoff; this.delete = delete; }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            if (Files.isSymbolicLink(directory) || attributes.isOther()
                    || !directory.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(workspace)) {
                skippedUnsafe++;
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            if (Files.isSymbolicLink(file) || attributes.isOther()
                    || !file.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(workspace)) {
                skippedUnsafe++;
                return FileVisitResult.CONTINUE;
            }
            if (attributes.isRegularFile() && DAILY_IMPORT_FILE.matcher(file.getFileName().toString()).matches()
                    && attributes.lastModifiedTime().toInstant().isBefore(cutoff)) {
                if (delete) Files.deleteIfExists(file);
                affected++;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            skippedUnsafe++;
            return FileVisitResult.CONTINUE;
        }
    }
}
