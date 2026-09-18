package dev.dhruv.jobsearch.privacy;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataLifecycleInventoryService {

    private static final List<DatabaseScope> DATABASE_SCOPES = List.of(
            db("resume_variant", Category.EXPLICIT_USER_RECORDS),
            db("job_opportunity", Category.EXPLICIT_USER_RECORDS),
            db("job_application", Category.EXPLICIT_USER_RECORDS),
            db("application_event", Category.EXPLICIT_USER_RECORDS),
            db("daily_priority_action", Category.TRANSIENT_IMPORT_DATA),
            db("network_contact", Category.EXPLICIT_USER_RECORDS),
            db("outreach_activity", Category.EXPLICIT_USER_RECORDS),
            db("referral_candidate", Category.EXPLICIT_USER_RECORDS),
            db("preparation_track", Category.EXPLICIT_USER_RECORDS),
            db("preparation_milestone", Category.EXPLICIT_USER_RECORDS),
            db("preparation_item", Category.EXPLICIT_USER_RECORDS),
            db("practice_session", Category.EXPLICIT_USER_RECORDS),
            db("daily_prep_commitment", Category.EXPLICIT_USER_RECORDS),
            db("preparation_sprint", Category.EXPLICIT_USER_RECORDS),
            db("preparation_sprint_item", Category.EXPLICIT_USER_RECORDS),
            db("preparation_track_resource", Category.EXPLICIT_USER_RECORDS),
            db("personal_skill_backlog", Category.EXPLICIT_USER_RECORDS),
            db("personal_skill_preparation_link", Category.EXPLICIT_USER_RECORDS),
            db("skill_learning_resource", Category.EXPLICIT_USER_RECORDS),
            db("skill_project_evidence", Category.EXPLICIT_USER_RECORDS),
            db("linkedin_connection", Category.TRANSIENT_IMPORT_DATA),
            db("weekly_review", Category.EXPLICIT_USER_RECORDS),
            db("weekly_metric_snapshot", Category.EXPLICIT_USER_RECORDS),
            db("weekly_review_revision", Category.EXPLICIT_USER_RECORDS),
            db("calendar_event", Category.EXPLICIT_USER_RECORDS),
            db("interview_round", Category.EXPLICIT_USER_RECORDS),
            db("application_artifact", Category.EXPLICIT_USER_RECORDS),
            db("job_skill_observation", Category.EXPLICIT_USER_RECORDS),
            db("local_user_profile", Category.EXPLICIT_USER_RECORDS),
            db("local_summary_preferences", Category.EXPLICIT_USER_RECORDS),

            db("import_batch", Category.TRANSIENT_IMPORT_DATA),
            db("opportunity_observation", Category.TRANSIENT_IMPORT_DATA),
            db("generic_inbox_item", Category.TRANSIENT_IMPORT_DATA),
            db("generic_inbox_candidate", Category.TRANSIENT_IMPORT_DATA),
            db("inbox_duplicate_match", Category.TRANSIENT_IMPORT_DATA),
            db("linkedin_connection_import_batch", Category.TRANSIENT_IMPORT_DATA),
            db("job_description_snapshot", Category.TRANSIENT_IMPORT_DATA),

            db("assistance_run", Category.DERIVED_CONTEXT),
            db("assistance_decision", Category.DERIVED_CONTEXT),

            db("privacy_policy", Category.AUDIT_AND_POLICY_METADATA),
            db("privacy_cleanup_receipt", Category.AUDIT_AND_POLICY_METADATA),
            db("transmission_preview", Category.AUDIT_AND_POLICY_METADATA),
            db("transmission_receipt", Category.AUDIT_AND_POLICY_METADATA),
            dbRetained("data_lifecycle_operation", Category.AUDIT_AND_POLICY_METADATA),

            dbRetained("canonical_skill", Category.APPLICATION_REFERENCE_DATA),
            dbRetained("skill_alias", Category.APPLICATION_REFERENCE_DATA));

    private final JdbcTemplate jdbc;
    private final Path workspaceRoot;
    private final List<FileScope> fileScopes;

    public DataLifecycleInventoryService(JdbcTemplate jdbc,
            @Value("${app.privacy.workspace-root}") String workspaceRoot,
            @Value("${app.application-artifacts.folder}") String resumes,
            @Value("${app.notes.folder}") String notes,
            @Value("${app.imports.daily-high-fit.folder}") String dailyImports,
            @Value("${app.imports.linkedin-connections.file}") String linkedInFile,
            @Value("${app.privacy.preparation-workspace-folder}") String preparationWorkspace,
            @Value("${app.cohorts.company-targets.folder}") String companyTargets) {
        this.jdbc = jdbc;
        this.workspaceRoot = absolute(workspaceRoot);
        this.fileScopes = List.of(
                file("application-resumes", Category.SENSITIVE_WORKSPACE_FILES, resumes),
                file("notes", Category.SENSITIVE_WORKSPACE_FILES, notes),
                file("linkedin-connections-import", Category.SENSITIVE_WORKSPACE_FILES, linkedInFile),
                file("preparation-workspace", Category.SENSITIVE_WORKSPACE_FILES, preparationWorkspace),
                file("daily-high-fit-imports", Category.TRANSIENT_IMPORT_DATA, dailyImports),
                file("company-target-inputs", Category.SENSITIVE_WORKSPACE_FILES, companyTargets));
    }

    @Transactional(readOnly = true)
    public InventoryView inventory() {
        Map<Category, MutableCategory> categories = new EnumMap<>(Category.class);
        for (Category category : Category.values()) categories.put(category, new MutableCategory(category));

        for (DatabaseScope scope : DATABASE_SCOPES) {
            long rows = jdbc.queryForObject("select count(*) from " + scope.table(), Long.class);
            categories.get(scope.category()).add(new ScopeView(scope.table(), StorageType.DATABASE_TABLE,
                    ScopeStatus.AVAILABLE, rows, 0, 0, 0, scope.deleteAllIncludes(),
                    databaseDescription(scope.category())));
        }
        for (FileScope scope : fileScopes) categories.get(scope.category()).add(inspect(scope));

        return new InventoryView(categories.values().stream().map(MutableCategory::view).toList(),
                DATABASE_SCOPES.size(), fileScopes.size(), boundaries());
    }

    List<DatabaseScope> databaseScopes(Set<Category> categories, boolean deletableOnly) {
        return DATABASE_SCOPES.stream()
                .filter(scope -> categories.contains(scope.category()))
                .filter(scope -> !deletableOnly || scope.deleteAllIncludes())
                .toList();
    }

    LifecycleFilePlan lifecycleFilePlan(Set<Category> categories) {
        Map<Path, PlannedFile> uniqueFiles = new LinkedHashMap<>();
        long skipped = 0;
        for (FileScope scope : fileScopes) {
            if (!categories.contains(scope.category())) continue;
            SafeFileCollector collector = collect(scope);
            skipped += collector.skippedUnsafeEntries;
            if (collector.rejectedStatus != null || collector.skippedUnsafeEntries > 0) {
                throw new IllegalStateException("Filesystem scope '" + scope.name()
                        + "' is not safe for export or deletion. Resolve the rejected or linked entry and preview again.");
            }
            for (PlannedFile file : collector.files) uniqueFiles.putIfAbsent(file.absolutePath(), file);
        }
        List<PlannedFile> files = uniqueFiles.values().stream()
                .sorted(Comparator.comparing(PlannedFile::archivePath)).toList();
        return new LifecycleFilePlan(files, files.stream().mapToLong(PlannedFile::size).sum(), skipped);
    }

    private SafeFileCollector collect(FileScope scope) {
        Path configured = absolute(scope.configuredPath());
        SafeFileCollector collector = new SafeFileCollector(scope.name());
        try {
            if (!Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(workspaceRoot)) {
                collector.rejectedStatus = ScopeStatus.WORKSPACE_UNAVAILABLE;
                return collector;
            }
            Path canonicalWorkspace = workspaceRoot.toRealPath();
            collector.workspace = canonicalWorkspace;
            if (!configured.startsWith(workspaceRoot)) {
                collector.rejectedStatus = ScopeStatus.REJECTED_OUTSIDE_WORKSPACE;
                return collector;
            }
            if (!Files.exists(configured, LinkOption.NOFOLLOW_LINKS)) {
                Path existingParent = configured.getParent();
                while (existingParent != null && !Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
                    existingParent = existingParent.getParent();
                }
                if (existingParent == null || Files.isSymbolicLink(existingParent)
                        || !existingParent.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(canonicalWorkspace)) {
                    collector.rejectedStatus = ScopeStatus.REJECTED_UNSAFE_LINK;
                }
                return collector;
            }
            if (Files.isSymbolicLink(configured)) {
                collector.rejectedStatus = ScopeStatus.REJECTED_UNSAFE_LINK;
                return collector;
            }
            Path canonicalTarget = configured.toRealPath();
            if (!canonicalTarget.startsWith(canonicalWorkspace)) {
                collector.rejectedStatus = ScopeStatus.REJECTED_OUTSIDE_WORKSPACE;
                return collector;
            }
            collector.root = Files.isDirectory(canonicalTarget, LinkOption.NOFOLLOW_LINKS)
                    ? canonicalTarget : canonicalTarget.getParent();
            if (Files.isDirectory(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
                Files.walkFileTree(canonicalTarget, collector);
            } else if (Files.isRegularFile(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
                collector.add(canonicalTarget);
            } else {
                collector.skippedUnsafeEntries++;
            }
        } catch (IOException | SecurityException exception) {
            collector.rejectedStatus = ScopeStatus.REJECTED_UNREADABLE;
        }
        return collector;
    }

    private ScopeView inspect(FileScope scope) {
        Path configured = absolute(scope.configuredPath());
        Path canonicalWorkspace;
        try {
            if (!Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(workspaceRoot)) {
                return rejected(scope, configured, ScopeStatus.WORKSPACE_UNAVAILABLE);
            }
            canonicalWorkspace = workspaceRoot.toRealPath();
            if (!configured.startsWith(workspaceRoot)) {
                return rejected(scope, configured, ScopeStatus.REJECTED_OUTSIDE_WORKSPACE);
            }
            if (!Files.exists(configured, LinkOption.NOFOLLOW_LINKS)) {
                Path existingParent = configured.getParent();
                while (existingParent != null && !Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
                    existingParent = existingParent.getParent();
                }
                if (existingParent == null || Files.isSymbolicLink(existingParent)) {
                    return rejected(scope, configured, ScopeStatus.REJECTED_UNSAFE_LINK);
                }
                Path canonicalParent = existingParent.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!canonicalParent.startsWith(canonicalWorkspace)) {
                    return rejected(scope, configured, ScopeStatus.REJECTED_OUTSIDE_WORKSPACE);
                }
                return new ScopeView(scope.name(), StorageType.FILESYSTEM_PATH, ScopeStatus.MISSING, 0, 0, 0, 0,
                        deleteAllIncludes(scope.category()), "Configured workspace scope (currently missing).");
            }
            if (Files.isSymbolicLink(configured)) return rejected(scope, configured, ScopeStatus.REJECTED_UNSAFE_LINK);
            // Follow parent links only for validation; traversal itself never follows links.
            Path canonicalTarget = configured.toRealPath();
            if (!canonicalTarget.startsWith(canonicalWorkspace)) {
                return rejected(scope, configured, ScopeStatus.REJECTED_OUTSIDE_WORKSPACE);
            }
            FileCounter counter = new FileCounter(canonicalWorkspace);
            if (Files.isDirectory(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
                Files.walkFileTree(canonicalTarget, counter);
            } else if (Files.isRegularFile(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
                counter.fileCount = 1;
                counter.byteCount = Files.size(canonicalTarget);
            } else {
                counter.skippedUnsafeEntries = 1;
            }
            ScopeStatus status = counter.skippedUnsafeEntries == 0
                    ? ScopeStatus.AVAILABLE : ScopeStatus.AVAILABLE_WITH_SKIPPED_UNSAFE_ENTRIES;
            return new ScopeView(scope.name(), StorageType.FILESYSTEM_PATH, status, 0, counter.fileCount,
                    counter.byteCount, counter.skippedUnsafeEntries, deleteAllIncludes(scope.category()),
                    "Application-managed workspace scope.");
        } catch (IOException | SecurityException exception) {
            return rejected(scope, configured, ScopeStatus.REJECTED_UNREADABLE);
        }
    }

    private ScopeView rejected(FileScope scope, Path configured, ScopeStatus status) {
        return new ScopeView(scope.name(), StorageType.FILESYSTEM_PATH, status, 0, 0, 0, 0, false,
                "Rejected workspace scope; no path or content was traversed.");
    }

    private static boolean deleteAllIncludes(Category category) {
        return category != Category.APPLICATION_REFERENCE_DATA;
    }

    private static String databaseDescription(Category category) {
        return switch (category) {
            case EXPLICIT_USER_RECORDS -> "Deliberately saved application records; separate confirmation required.";
            case TRANSIENT_IMPORT_DATA -> "Import and review staging records.";
            case DERIVED_CONTEXT -> "Application-derived intelligence and assistance context.";
            case AUDIT_AND_POLICY_METADATA -> "Payload-free policy and cleanup accountability metadata.";
            case APPLICATION_REFERENCE_DATA -> "Non-personal application catalog data retained across delete-all.";
            case SENSITIVE_WORKSPACE_FILES -> "Workspace files are inventoried separately.";
        };
    }

    private static List<BoundaryView> boundaries() {
        return List.of(
                boundary("Codex task history and memories", "Controlled by Codex and account-level settings."),
                boundary("External AI/provider processing", "Controlled by the provider after an opted-in transmission."),
                boundary("Host browser data", "History, downloads, cookies, and external-account activity remain browser-controlled."),
                boundary("Copies and backups", "Exports, screenshots, synchronized folders, and user-created backups cannot be recalled."),
                boundary("Unmounted workspace infrastructure", "The .env file, PostgreSQL data directory, and backups are not mounted into the API and are never traversed."),
                boundary("Physical storage remnants", "Row or file deletion cannot guarantee SSD, filesystem snapshot, or PostgreSQL block erasure."),
                boundary("Platform telemetry", "Operating system, Docker, registry, and connected-service telemetry use separate controls."));
    }

    private static DatabaseScope db(String table, Category category) { return new DatabaseScope(table, category, true); }
    private static DatabaseScope dbRetained(String table, Category category) { return new DatabaseScope(table, category, false); }
    private static FileScope file(String name, Category category, String path) { return new FileScope(name, category, path); }
    private static BoundaryView boundary(String name, String description) { return new BoundaryView(name, description, false); }
    private static Path absolute(String value) { return Path.of(value).toAbsolutePath().normalize(); }

    public enum Category {
        EXPLICIT_USER_RECORDS,
        SENSITIVE_WORKSPACE_FILES,
        TRANSIENT_IMPORT_DATA,
        DERIVED_CONTEXT,
        AUDIT_AND_POLICY_METADATA,
        APPLICATION_REFERENCE_DATA
    }

    public enum StorageType { DATABASE_TABLE, FILESYSTEM_PATH }
    public enum ScopeStatus {
        AVAILABLE,
        AVAILABLE_WITH_SKIPPED_UNSAFE_ENTRIES,
        MISSING,
        WORKSPACE_UNAVAILABLE,
        REJECTED_OUTSIDE_WORKSPACE,
        REJECTED_UNSAFE_LINK,
        REJECTED_UNREADABLE
    }

    public record InventoryView(List<CategoryView> categories, int databaseTableCount,
            int configuredFilesystemScopeCount, List<BoundaryView> outsideApplicationControl) {}
    public record CategoryView(Category category, long databaseRowCount, long fileCount, long byteCount,
            long skippedUnsafeEntryCount, List<ScopeView> scopes) {}
    public record ScopeView(String name, StorageType storageType, ScopeStatus status, long recordCount,
            long fileCount, long byteCount, long skippedUnsafeEntryCount, boolean includedInDeleteAll,
            String scope) {}
    public record BoundaryView(String name, String description, boolean removableByApplication) {}

    record DatabaseScope(String table, Category category, boolean deleteAllIncludes) {}
    private record FileScope(String name, Category category, String configuredPath) {}

    record PlannedFile(String scopeName, Path absolutePath, Path canonicalWorkspaceRoot,
            String archivePath, long size, long modifiedAtMillis, String fileKey) {}
    record LifecycleFilePlan(List<PlannedFile> files, long byteCount, long skippedUnsafeEntryCount) {}

    private static final class MutableCategory {
        private final Category category;
        private final List<ScopeView> scopes = new ArrayList<>();
        private MutableCategory(Category category) { this.category = category; }
        private void add(ScopeView scope) { scopes.add(scope); }
        private CategoryView view() {
            return new CategoryView(category,
                    scopes.stream().mapToLong(ScopeView::recordCount).sum(),
                    scopes.stream().mapToLong(ScopeView::fileCount).sum(),
                    scopes.stream().mapToLong(ScopeView::byteCount).sum(),
                    scopes.stream().mapToLong(ScopeView::skippedUnsafeEntryCount).sum(), List.copyOf(scopes));
        }
    }

    private static final class FileCounter extends SimpleFileVisitor<Path> {
        private final Path workspace;
        private long fileCount;
        private long byteCount;
        private long skippedUnsafeEntries;

        private FileCounter(Path workspace) { this.workspace = workspace; }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            if (!directory.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(workspace)
                    || Files.isSymbolicLink(directory) || attributes.isOther()) {
                skippedUnsafeEntries++;
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            if (Files.isSymbolicLink(file) || attributes.isOther()
                    || !file.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(workspace)) {
                skippedUnsafeEntries++;
                return FileVisitResult.CONTINUE;
            }
            if (attributes.isRegularFile()) {
                fileCount++;
                byteCount += attributes.size();
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            skippedUnsafeEntries++;
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class SafeFileCollector extends SimpleFileVisitor<Path> {
        private final String scopeName;
        private final List<PlannedFile> files = new ArrayList<>();
        private Path root;
        private Path workspace;
        private long skippedUnsafeEntries;
        private ScopeStatus rejectedStatus;

        private SafeFileCollector(String scopeName) { this.scopeName = scopeName; }

        private void add(Path file) throws IOException {
            Path real = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(workspace) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                skippedUnsafeEntries++;
                return;
            }
            Path relative = root == null ? file.getFileName() : root.relativize(real);
            String archive = "files/" + scopeName + "/" + relative.toString().replace('\\', '/');
            BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            files.add(new PlannedFile(scopeName, real, workspace, archive, attributes.size(),
                    attributes.lastModifiedTime().toMillis(),
                    attributes.fileKey() == null ? null : attributes.fileKey().toString()));
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            if (Files.isSymbolicLink(directory) || attributes.isOther()) {
                skippedUnsafeEntries++;
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            if (Files.isSymbolicLink(file) || attributes.isOther()) {
                skippedUnsafeEntries++;
                return FileVisitResult.CONTINUE;
            }
            if (attributes.isRegularFile()) add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            skippedUnsafeEntries++;
            return FileVisitResult.CONTINUE;
        }
    }
}
