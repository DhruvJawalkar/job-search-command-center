package dev.dhruv.jobsearch.privacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.Category;
import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.DatabaseScope;
import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.LifecycleFilePlan;
import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.PlannedFile;
import dev.dhruv.jobsearch.privacy.DataLifecycleOperation.OperationStatus;
import dev.dhruv.jobsearch.privacy.DataLifecycleOperation.OperationType;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class DataLifecycleService {

    static final String PREVIEW_HEADER = "preview-data-lifecycle";
    static final String EXPORT_HEADER = "export-application-data";
    static final String DELETE_HEADER = "delete-application-data";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ReentrantLock EXCLUSIVE_OPERATION = new ReentrantLock();
    private static final Set<Category> DELETE_ALL_CATEGORIES = EnumSet.of(
            Category.EXPLICIT_USER_RECORDS, Category.SENSITIVE_WORKSPACE_FILES,
            Category.TRANSIENT_IMPORT_DATA, Category.DERIVED_CONTEXT, Category.AUDIT_AND_POLICY_METADATA);
    private static final List<String> DELETE_ORDER = List.of(
            "assistance_decision", "assistance_run",
            "inbox_duplicate_match", "generic_inbox_candidate", "generic_inbox_item",
            "daily_priority_action", "opportunity_observation", "import_batch",
            "linkedin_connection", "linkedin_connection_import_batch",
            "job_skill_observation", "job_description_snapshot",
            "application_event", "application_artifact", "interview_round",
            "referral_candidate", "outreach_activity",
            "personal_skill_preparation_link", "skill_learning_resource", "skill_project_evidence",
            "personal_skill_backlog", "preparation_sprint_item", "preparation_sprint",
            "practice_session", "daily_prep_commitment", "preparation_item",
            "preparation_track_resource", "preparation_milestone", "preparation_track",
            "weekly_metric_snapshot", "weekly_review_revision", "weekly_review",
            "job_application", "calendar_event", "network_contact", "job_opportunity", "resume_variant",
            "local_summary_preferences", "local_user_profile",
            "transmission_receipt", "transmission_preview",
            "privacy_cleanup_receipt", "privacy_policy");

    private final DataLifecycleInventoryService inventory;
    private final DataLifecycleOperationRepository operations;
    private final WorkspaceFileOperator fileOperator;
    private final SafeWorkspaceFileReader fileReader;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public DataLifecycleService(DataLifecycleInventoryService inventory,
            DataLifecycleOperationRepository operations, WorkspaceFileOperator fileOperator,
            SafeWorkspaceFileReader fileReader, JdbcTemplate jdbc, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this(inventory, operations, fileOperator, fileReader, jdbc, objectMapper,
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    DataLifecycleService(DataLifecycleInventoryService inventory,
            DataLifecycleOperationRepository operations, WorkspaceFileOperator fileOperator,
            SafeWorkspaceFileReader fileReader, JdbcTemplate jdbc, ObjectMapper objectMapper,
            TransactionTemplate transactions, Clock clock) {
        this.inventory = inventory;
        this.operations = operations;
        this.fileOperator = fileOperator;
        this.fileReader = fileReader;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    public PreviewView preview(PreviewCommand command) {
        OperationType type = command.operationType();
        Set<Category> requested = normalizeRequested(type, command.categories());
        Set<Category> effective = effectiveCategories(type, requested);
        boolean deletion = type != OperationType.EXPORT;
        Plan plan = computePlan(type, effective, deletion, null);
        if (deletion && plan.databaseRows() == 0 && plan.files().files().isEmpty()) {
            throw new IllegalStateException("The selected application-owned scope is already empty.");
        }

        String token = token();
        String phrase = deletion
                ? "DELETE " + plan.databaseRows() + " ROWS AND " + plan.files().files().size() + " FILES"
                : null;
        Instant now = clock.instant();
        DataLifecycleOperation operation = new DataLifecycleOperation(type, effective, plan.digest(), sha256(token),
                phrase, plan.databaseRows(), plan.files().files().size(), plan.files().byteCount(),
                plan.files().skippedUnsafeEntryCount(), now, now.plus(10, ChronoUnit.MINUTES));
        operations.save(operation);
        return previewView(operation, token, requested, effective, plan);
    }

    public ExportResult export(UUID operationId, String previewToken) {
        EXCLUSIVE_OPERATION.lock();
        try {
            DataLifecycleOperation operation = requirePreview(operationId, previewToken, OperationType.EXPORT, null);
            Plan plan = computePlan(OperationType.EXPORT, operation.categories(), false, operation.getId());
            requireUnchanged(operation, plan);
            byte[] archive = createArchive(operation, plan);
            operation.markExported(archive.length, clock.instant());
            operations.save(operation);
            return new ExportResult("job-search-command-center-export-" + operation.getId() + ".zip", archive,
                    receipt(operation));
        } finally {
            EXCLUSIVE_OPERATION.unlock();
        }
    }

    public OperationReceiptView delete(UUID operationId, String previewToken, String confirmationPhrase) {
        EXCLUSIVE_OPERATION.lock();
        try {
            DataLifecycleOperation current = find(operationId);
            verifyToken(current, previewToken);
            if (current.getStatus() == OperationStatus.COMPLETED) return receipt(current);
            if (current.getStatus() == OperationStatus.PARTIAL_FILESYSTEM_FAILURE
                    || current.getStatus() == OperationStatus.DATABASE_DELETED) {
                throw new IllegalStateException("Filesystem deletion was incomplete. Create a fresh preview and confirmation token for the remaining files.");
            }
            DataLifecycleOperation operation = requirePreview(operationId, previewToken, null, confirmationPhrase);
            if (operation.getOperationType() == OperationType.EXPORT) {
                throw new IllegalArgumentException("An export preview cannot authorize deletion.");
            }
            Plan plan = computePlan(operation.getOperationType(), operation.categories(), true, operation.getId());
            requireUnchanged(operation, plan);
            try {
                transactions.executeWithoutResult(status -> applyDatabaseDeletion(operation.getId(), plan));
            } catch (RuntimeException exception) {
                DataLifecycleOperation failed = find(operation.getId());
                if (failed.getStatus() == OperationStatus.PREVIEWED) {
                    failed.markFailed(clock.instant());
                    operations.save(failed);
                }
                throw exception;
            }
            DataLifecycleOperation databaseDeleted = find(operation.getId());
            WorkspaceFileOperator.DeleteOutcome outcome = fileOperator.delete(plan.files().files());
            databaseDeleted.markDeleteFinished(outcome.deletedFileCount(), outcome.failedFileCount(), clock.instant());
            operations.save(databaseDeleted);
            return receipt(databaseDeleted);
        } finally {
            EXCLUSIVE_OPERATION.unlock();
        }
    }

    public OperationReceiptView receipt(UUID operationId) { return receipt(find(operationId)); }

    private void applyDatabaseDeletion(UUID operationId, Plan originalPlan) {
        DataLifecycleOperation operation = find(operationId);
        Plan current = computePlan(operation.getOperationType(), operation.categories(), true, operation.getId());
        if (!MessageDigest.isEqual(originalPlan.digest().getBytes(StandardCharsets.US_ASCII),
                current.digest().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("Application data changed after preview. Create and confirm a new preview.");
        }
        Map<String, DatabaseScope> selected = new LinkedHashMap<>();
        for (DatabaseScope scope : originalPlan.databaseScopes()) selected.put(scope.table(), scope);
        long affected = 0;
        for (String table : DELETE_ORDER) {
            if (selected.remove(table) != null) affected += deleteScope(table, operation.categories());
        }
        if (!selected.isEmpty()) {
            throw new IllegalStateException("The deletion order does not cover every selected application table.");
        }
        if (affected != originalPlan.databaseRows()) {
            throw new IllegalStateException("Application data changed while deletion was running; database changes were rolled back.");
        }
        operation.markDatabaseDeleted(affected);
        operations.save(operation);
    }

    private Plan computePlan(OperationType type, Set<Category> categories, boolean deletion, UUID operationToExclude) {
        List<DatabaseScope> scopes = inventory.databaseScopes(categories, deletion);
        LifecycleFilePlan files = inventory.lifecycleFilePlan(categories);
        MessageDigest digest = digest();
        update(digest, type.name());
        categories.stream().map(Enum::name).sorted().forEach(value -> update(digest, value));
        long rows = 0;
        List<ScopePlanView> scopeViews = new ArrayList<>();
        for (DatabaseScope scope : scopes.stream().sorted(Comparator.comparing(DatabaseScope::table)).toList()) {
            List<String> ids = plannedIds(scope.table(), categories, deletion, operationToExclude);
            rows += ids.size();
            update(digest, scope.table());
            ids.forEach(id -> update(digest, id));
            scopeViews.add(new ScopePlanView(scope.table(), "DATABASE_TABLE", ids.size(), 0, 0));
        }
        Map<String, List<PlannedFile>> byScope = new LinkedHashMap<>();
        for (PlannedFile file : files.files()) {
            update(digest, file.archivePath());
            update(digest, Long.toString(file.size()));
            update(digest, Long.toString(file.modifiedAtMillis()));
            update(digest, file.fileKey() == null ? "unavailable-file-key" : file.fileKey());
            byScope.computeIfAbsent(file.scopeName(), ignored -> new ArrayList<>()).add(file);
        }
        byScope.forEach((name, planned) -> scopeViews.add(new ScopePlanView(name, "FILESYSTEM_PATH", 0,
                planned.size(), planned.stream().mapToLong(PlannedFile::size).sum())));
        return new Plan(scopes, files, rows, hex(digest.digest()), List.copyOf(scopeViews));
    }

    private byte[] createArchive(DataLifecycleOperation operation, Plan plan) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("exportedAt", clock.instant().toString());
                manifest.put("operationId", operation.getId().toString());
                manifest.put("categories", operation.categories().stream().map(Enum::name).sorted().toList());
                manifest.put("databaseRows", plan.databaseRows());
                manifest.put("fileCount", plan.files().files().size());
                manifest.put("byteCount", plan.files().byteCount());
                manifest.put("boundaries", inventory.inventory().outsideApplicationControl());
                write(zip, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
                for (DatabaseScope scope : plan.databaseScopes()) {
                    List<Map<String, Object>> rows;
                    if (scope.table().equals("data_lifecycle_operation")) {
                        rows = jdbc.queryForList("select * from data_lifecycle_operation where id <> ? order by created_at",
                                operation.getId());
                    } else {
                        rows = jdbc.queryForList("select * from " + scope.table() + " order by id");
                    }
                    write(zip, "database/" + scope.table() + ".json",
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows));
                }
                for (PlannedFile file : plan.files().files()) {
                    byte[] content = fileReader.read(file);
                    zip.putNextEntry(new ZipEntry(file.archivePath()));
                    zip.write(content);
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The local export archive could not be created.", exception);
        }
    }

    private List<String> plannedIds(String table, Set<Category> categories, boolean deletion,
            UUID operationToExclude) {
        if (deletion && table.equals("job_description_snapshot")
                && !categories.contains(Category.EXPLICIT_USER_RECORDS)) {
            return jdbc.query("""
                    select snapshot.id from job_description_snapshot snapshot
                    where not exists (
                        select 1 from job_skill_observation observation where observation.snapshot_id = snapshot.id
                    )
                    order by snapshot.id
                    """, (rs, index) -> rs.getString(1));
        }
        if (operationToExclude != null && table.equals("data_lifecycle_operation")) {
            return jdbc.query("select id from data_lifecycle_operation where id <> ? order by id",
                    (rs, index) -> rs.getString(1), operationToExclude);
        }
        return jdbc.query("select id from " + table + " order by id", (rs, index) -> rs.getString(1));
    }

    private int deleteScope(String table, Set<Category> categories) {
        if (table.equals("job_description_snapshot") && !categories.contains(Category.EXPLICIT_USER_RECORDS)) {
            return jdbc.update("""
                    delete from job_description_snapshot snapshot
                    where not exists (
                        select 1 from job_skill_observation observation where observation.snapshot_id = snapshot.id
                    )
                    """);
        }
        return jdbc.update("delete from " + table);
    }

    private void write(ZipOutputStream zip, String name, byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
    }

    private DataLifecycleOperation requirePreview(UUID id, String previewToken, OperationType requiredType,
            String phrase) {
        DataLifecycleOperation operation = find(id);
        verifyToken(operation, previewToken);
        if (requiredType != null && operation.getOperationType() != requiredType) {
            throw new IllegalArgumentException("The preview does not authorize this operation.");
        }
        if (operation.getStatus() != OperationStatus.PREVIEWED) {
            throw new IllegalStateException("This preview is no longer available. Create a new preview.");
        }
        Instant now = clock.instant();
        if (!operation.getExpiresAt().isAfter(now)) {
            operation.markExpired(now);
            operations.save(operation);
            throw new IllegalStateException("This preview expired. Create a new preview.");
        }
        if (phrase != null) requirePhrase(operation, phrase);
        return operation;
    }

    private void requireUnchanged(DataLifecycleOperation operation, Plan plan) {
        if (!MessageDigest.isEqual(operation.getPlanDigest().getBytes(StandardCharsets.US_ASCII),
                plan.digest().getBytes(StandardCharsets.US_ASCII))) {
            operation.markStale(clock.instant());
            operations.save(operation);
            throw new IllegalStateException("Application data changed after preview. Create and confirm a new preview.");
        }
    }

    private void verifyToken(DataLifecycleOperation operation, String token) {
        if (token == null || !MessageDigest.isEqual(operation.getConfirmationTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("The preview token is missing or invalid.");
        }
    }

    private void requirePhrase(DataLifecycleOperation operation, String phrase) {
        if (operation.getConfirmationPhrase() == null || !operation.getConfirmationPhrase().equals(phrase)) {
            throw new IllegalArgumentException("Type the exact confirmation phrase shown by the preview.");
        }
    }

    private DataLifecycleOperation find(UUID id) {
        return operations.findById(id)
                .orElseThrow(() -> new NotFoundException("Data-lifecycle operation " + id + " was not found."));
    }

    private Set<Category> normalizeRequested(OperationType type, Set<Category> categories) {
        if (type == OperationType.DELETE_ALL && (categories == null || categories.isEmpty())) {
            return EnumSet.noneOf(Category.class);
        }
        if (categories == null || categories.isEmpty()) throw new IllegalArgumentException("Choose at least one data category.");
        return EnumSet.copyOf(categories);
    }

    private Set<Category> effectiveCategories(OperationType type, Set<Category> requested) {
        if (type == null) throw new IllegalArgumentException("Choose export, category deletion, or delete all.");
        if (type == OperationType.DELETE_ALL) return EnumSet.copyOf(DELETE_ALL_CATEGORIES);
        EnumSet<Category> effective = EnumSet.copyOf(requested);
        if (type == OperationType.DELETE_CATEGORIES) {
            if (effective.contains(Category.APPLICATION_REFERENCE_DATA)) {
                throw new IllegalArgumentException("Application reference catalogs are retained and cannot be deleted.");
            }
            // Imported/review staging rows reference explicit opportunities and are included to avoid hidden cascades.
            if (effective.contains(Category.EXPLICIT_USER_RECORDS)) effective.add(Category.TRANSIENT_IMPORT_DATA);
        }
        return effective;
    }

    private PreviewView previewView(DataLifecycleOperation operation, String token, Set<Category> requested,
            Set<Category> effective, Plan plan) {
        return new PreviewView(operation.getId(), operation.getOperationType(), requested, effective,
                plan.databaseRows(), plan.files().files().size(), plan.files().byteCount(),
                plan.files().skippedUnsafeEntryCount(), plan.scopeViews(), operation.getConfirmationPhrase(), token,
                operation.getExpiresAt(), List.of("canonical_skill", "skill_alias", "data_lifecycle_operation"),
                inventory.inventory().outsideApplicationControl());
    }

    private OperationReceiptView receipt(DataLifecycleOperation value) {
        return new OperationReceiptView(value.getId(), value.getOperationType(), value.categories(), value.getStatus(),
                value.getPlannedDatabaseRows(), value.getPlannedFileCount(), value.getPlannedByteCount(),
                value.getAffectedDatabaseRows(), value.getDeletedFileCount(), value.getFailedFileCount(),
                value.getExportByteCount(), value.getCreatedAt(), value.getCompletedAt(),
                List.of("Codex tasks and memories", "host-browser data", "provider-side data",
                        "copies and unmounted backups", "physical storage remnants"));
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String sha256(String value) {
        MessageDigest digest = digest();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return hex(digest.digest());
    }
    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }

    public record PreviewCommand(OperationType operationType, Set<Category> categories) {}
    public record PreviewView(UUID operationId, OperationType operationType, Set<Category> requestedCategories,
            Set<Category> effectiveCategories, long databaseRows, long fileCount, long byteCount,
            long skippedUnsafeEntryCount, List<ScopePlanView> scopes, String confirmationPhrase,
            String previewToken, Instant expiresAt, List<String> retainedApplicationScopes,
            List<DataLifecycleInventoryService.BoundaryView> outsideApplicationControl) {}
    public record ScopePlanView(String name, String storageType, long recordCount, long fileCount, long byteCount) {}
    public record ExecuteCommand(UUID operationId, String previewToken, String confirmationPhrase) {}
    public record OperationReceiptView(UUID operationId, OperationType operationType, Set<Category> categories,
            OperationStatus status, long plannedDatabaseRows, long plannedFileCount, long plannedByteCount,
            Long affectedDatabaseRows, Long deletedFileCount, Long failedFileCount, Long exportByteCount,
            Instant createdAt, Instant completedAt, List<String> outsideApplicationControl) {}
    public record ExportResult(String filename, byte[] archive, OperationReceiptView receipt) {}
    private record Plan(List<DatabaseScope> databaseScopes, LifecycleFilePlan files, long databaseRows,
            String digest, List<ScopePlanView> scopeViews) {}
}
