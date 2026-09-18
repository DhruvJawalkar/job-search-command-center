package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.Test;

class SafeWorkspaceFileReaderTest {

    @Test
    void readsThroughSecureDirectoryHandlesOrFailsClosedWhenTheProviderCannotSupplyThem() throws Exception {
        Path workspace = Files.createTempDirectory("lifecycle-export-workspace").toRealPath();
        Path file = Files.writeString(workspace.resolve("resume.txt"), "private resume").toRealPath();
        var planned = planned(workspace, file);

        if (secureDirectoriesAvailable(workspace)) {
            assertThat(new SafeWorkspaceFileReader().read(planned)).isEqualTo("private resume".getBytes());
        } else {
            assertThatThrownBy(() -> new SafeWorkspaceFileReader().read(planned))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("disabled on this runtime");
        }
    }

    @Test
    void rejectsAParentDirectoryReplacedByASymlinkBeforeExport() throws Exception {
        Path workspace = Files.createTempDirectory("lifecycle-export-swap-workspace").toRealPath();
        assumeTrue(secureDirectoriesAvailable(workspace), "Secure directory handles are unavailable on this provider.");
        Path scope = Files.createDirectories(workspace.resolve("notes"));
        Path file = Files.writeString(scope.resolve("private.txt"), "private content").toRealPath();
        var planned = planned(workspace, file);
        Path outside = Files.createTempDirectory("lifecycle-export-swap-outside").toRealPath();
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

        assertThatThrownBy(() -> new SafeWorkspaceFileReader().read(planned))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("opened safely");
        assertThat(Files.readString(moved.resolve("private.txt"))).isEqualTo("private content");
    }

    private boolean secureDirectoriesAvailable(Path workspace) throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspace.getRoot())) {
            return stream instanceof SecureDirectoryStream<?>;
        }
    }

    private DataLifecycleInventoryService.PlannedFile planned(Path workspace, Path file) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        return new DataLifecycleInventoryService.PlannedFile("test", file, workspace,
                "files/test/" + file.getFileName(), attributes.size(), attributes.lastModifiedTime().toMillis(),
                attributes.fileKey() == null ? null : attributes.fileKey().toString());
    }
}
