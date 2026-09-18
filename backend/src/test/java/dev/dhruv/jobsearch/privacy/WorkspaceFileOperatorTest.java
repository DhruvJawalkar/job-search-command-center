package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorkspaceFileOperatorTest {

    @Test
    void reportsPartialFailureWithoutDisclosingOrDeletingTheFailedFile() throws Exception {
        Path workspace = Files.createTempDirectory("lifecycle-delete-partial").toRealPath();
        Path first = Files.writeString(workspace.resolve("first.txt"), "first").toRealPath();
        Path second = Files.writeString(workspace.resolve("second.txt"), "second").toRealPath();
        var files = List.of(planned(workspace, first), planned(workspace, second));
        WorkspaceFileOperator operator = new WorkspaceFileOperator(path -> {
            if (path.equals(second)) throw new java.io.IOException("synthetic failure");
            return Files.deleteIfExists(path);
        });

        var outcome = operator.delete(files);

        assertThat(outcome.deletedFileCount()).isOne();
        assertThat(outcome.failedFileCount()).isOne();
        assertThat(first).doesNotExist();
        assertThat(second).exists();
    }

    @Test
    void rejectsAParentReplacedByASymlinkAfterPreview() throws Exception {
        Path workspace = Files.createTempDirectory("lifecycle-delete-workspace").toRealPath();
        Path scope = Files.createDirectories(workspace.resolve("notes"));
        Path original = Files.writeString(scope.resolve("note.txt"), "private note").toRealPath();
        var planned = planned(workspace, original);
        Path outside = Files.createTempDirectory("lifecycle-delete-outside").toRealPath();
        Path moved = outside.resolve("notes");
        Files.move(scope, moved);
        boolean linked;
        try {
            Files.createSymbolicLink(scope, moved);
            linked = true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            linked = false;
        }
        assumeTrue(linked, "Symbolic links are unavailable on this test host.");

        var outcome = new WorkspaceFileOperator().delete(List.of(planned));

        assertThat(outcome.deletedFileCount()).isZero();
        assertThat(outcome.failedFileCount()).isOne();
        assertThat(moved.resolve("note.txt")).exists();
    }

    private DataLifecycleInventoryService.PlannedFile planned(Path workspace, Path file) throws Exception {
        var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        return new DataLifecycleInventoryService.PlannedFile("notes", file, workspace,
                "files/notes/" + file.getFileName(), attributes.size(), attributes.lastModifiedTime().toMillis(),
                attributes.fileKey() == null ? null : attributes.fileKey().toString());
    }
}
