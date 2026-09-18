package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.EnumSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.dhruv.jobsearch.profile.LocalUserProfile;
import dev.dhruv.jobsearch.profile.LocalUserProfileService;

@SpringBootTest
class DataLifecycleInventoryFlowTest {

    private static final Path WORKSPACE = temporaryDirectory("lifecycle-workspace");
    private static final Path OUTSIDE = temporaryDirectory("lifecycle-outside");
    private static final Path COMPANY_TARGET_PATH = unsafeMissingCompanyTargetPath();
    private static boolean symbolicLinkCreated;

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
        registry.add("app.cohorts.company-targets.folder", COMPANY_TARGET_PATH::toString);
    }

    @BeforeAll
    static void createFileFixture() throws Exception {
        Files.createDirectories(WORKSPACE.resolve("application-resumes"));
        Files.createDirectories(WORKSPACE.resolve("notes"));
        Files.createDirectories(WORKSPACE.resolve("daily-high-fit-job-roles"));
        Files.createDirectories(WORKSPACE.resolve("linkedin-data-import"));
        Files.createDirectories(WORKSPACE.resolve("preparation-workspace"));
        Files.writeString(WORKSPACE.resolve("application-resumes/resume.pdf"), "resume");
        Files.writeString(WORKSPACE.resolve("notes/interview.txt"), "notes");
        Files.writeString(WORKSPACE.resolve("daily-high-fit-job-roles/openings.xlsx"), "fixture");
        Files.writeString(WORKSPACE.resolve("linkedin-data-import/Connections.csv"), "fixture");
        Files.writeString(WORKSPACE.resolve("preparation-workspace/plan.md"), "plan");
        Path outsideFile = Files.writeString(OUTSIDE.resolve("outside.txt"), "outside");
        try {
            Files.createSymbolicLink(WORKSPACE.resolve("notes/outside-link.txt"), outsideFile);
            symbolicLinkCreated = true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ignored) {
            symbolicLinkCreated = false;
        }
    }

    @Autowired DataLifecycleInventoryService inventory;
    @Autowired DataLifecycleService lifecycle;
    @Autowired PrivacyPolicyController controller;
    @Autowired LocalUserProfileService profiles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void classifiesEveryApplicationTableAndConfiguredPathWithoutMutation() throws Exception {
        profiles.save(new LocalUserProfile.ProfileValues("Inventory User", null, null, null, null, null, null,
                null, null, null, null, null, null, null, true));
        insertAssistanceRun();
        long databaseRowsBefore = applicationRowCount();
        Path resume = WORKSPACE.resolve("application-resumes/resume.pdf");
        FileTime modifiedBefore = Files.getLastModifiedTime(resume);

        var result = controller.dataInventory();

        assertThat(result.databaseTableCount()).isEqualTo(46);
        assertThat(result.configuredFilesystemScopeCount()).isEqualTo(6);
        assertThat(result.categories()).flatExtracting(DataLifecycleInventoryService.CategoryView::scopes)
                .filteredOn(scope -> scope.storageType() == DataLifecycleInventoryService.StorageType.DATABASE_TABLE)
                .extracting(DataLifecycleInventoryService.ScopeView::name).doesNotHaveDuplicates().hasSize(46);
        assertThat(category(result, DataLifecycleInventoryService.Category.EXPLICIT_USER_RECORDS).databaseRowCount())
                .isGreaterThanOrEqualTo(1);
        assertThat(category(result, DataLifecycleInventoryService.Category.DERIVED_CONTEXT).databaseRowCount())
                .isGreaterThanOrEqualTo(1);
        assertThat(category(result, DataLifecycleInventoryService.Category.SENSITIVE_WORKSPACE_FILES).fileCount())
                .isEqualTo(4);
        assertThat(result.outsideApplicationControl()).hasSize(7)
                .allMatch(boundary -> !boundary.removableByApplication());
        assertThat(result.categories()).flatExtracting(DataLifecycleInventoryService.CategoryView::scopes)
                .filteredOn(scope -> scope.storageType() == DataLifecycleInventoryService.StorageType.FILESYSTEM_PATH)
                .allMatch(scope -> !scope.scope().contains(WORKSPACE.toString()));

        var outside = scope(result, "company-target-inputs");
        assertThat(outside.status()).isIn(
                DataLifecycleInventoryService.ScopeStatus.REJECTED_OUTSIDE_WORKSPACE,
                DataLifecycleInventoryService.ScopeStatus.REJECTED_UNSAFE_LINK);
        assertThat(outside.fileCount()).isZero();
        assertThat(outside.includedInDeleteAll()).isFalse();

        var notes = scope(result, "notes");
        if (symbolicLinkCreated) {
            assertThat(notes.status())
                    .isEqualTo(DataLifecycleInventoryService.ScopeStatus.AVAILABLE_WITH_SKIPPED_UNSAFE_ENTRIES);
            assertThat(notes.skippedUnsafeEntryCount()).isOne();
        }

        assertThat(applicationRowCount()).isEqualTo(databaseRowsBefore);
        assertThat(Files.getLastModifiedTime(resume)).isEqualTo(modifiedBefore);
        assertThat(Files.readString(resume)).isEqualTo("resume");
    }

    @Test
    void rejectsADataLifecyclePreviewWhenASelectedScopeLeavesTheCanonicalWorkspace() {
        assertThatThrownBy(() -> lifecycle.preview(new DataLifecycleService.PreviewCommand(
                DataLifecycleOperation.OperationType.EXPORT,
                EnumSet.of(DataLifecycleInventoryService.Category.SENSITIVE_WORKSPACE_FILES))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not safe for export or deletion");
    }

    private DataLifecycleInventoryService.CategoryView category(DataLifecycleInventoryService.InventoryView result,
            DataLifecycleInventoryService.Category category) {
        return result.categories().stream().filter(value -> value.category() == category).findFirst().orElseThrow();
    }

    private DataLifecycleInventoryService.ScopeView scope(DataLifecycleInventoryService.InventoryView result,
            String name) {
        return result.categories().stream().flatMap(category -> category.scopes().stream())
                .filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }

    private void insertAssistanceRun() {
        Instant now = Instant.now();
        jdbc.update("""
                insert into assistance_run
                    (id, use_case, target_id, input_hash, provider, model, prompt_version, schema_version,
                     status, result_payload, created_at, completed_at)
                values (?, 'WEEKLY_REFLECTION', ?, ?, 'fixture', 'fixture', 'fixture', 'fixture',
                        'COMPLETED', '{}', ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString().replace("-", ""),
                Timestamp.from(now), Timestamp.from(now));
    }

    private long applicationRowCount() {
        return inventory.inventory().categories().stream()
                .mapToLong(DataLifecycleInventoryService.CategoryView::databaseRowCount).sum();
    }

    private static Path temporaryDirectory(String prefix) {
        try { return Files.createTempDirectory(prefix).toAbsolutePath().normalize(); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static Path unsafeMissingCompanyTargetPath() {
        Path link = WORKSPACE.resolve("linked-outside");
        try {
            Files.createSymbolicLink(link, OUTSIDE);
            return link.resolve("missing-company-targets");
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ignored) {
            return OUTSIDE.resolve("missing-company-targets");
        }
    }
}
