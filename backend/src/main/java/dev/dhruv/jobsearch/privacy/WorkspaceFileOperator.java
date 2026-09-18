package dev.dhruv.jobsearch.privacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class WorkspaceFileOperator {

    private final DeleteAction deleteAction;

    public WorkspaceFileOperator() { this(Files::deleteIfExists); }

    WorkspaceFileOperator(DeleteAction deleteAction) { this.deleteAction = deleteAction; }

    DeleteOutcome delete(List<DataLifecycleInventoryService.PlannedFile> files) {
        long deleted = 0;
        long failed = 0;
        for (DataLifecycleInventoryService.PlannedFile file : files) {
            try {
                Path path = file.absolutePath();
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
                Path currentCanonical = path.toRealPath();
                if (Files.isSymbolicLink(path) || !currentCanonical.equals(path)
                        || !currentCanonical.startsWith(file.canonicalWorkspaceRoot())
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(path) != file.size()
                        || Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() != file.modifiedAtMillis()) {
                    failed++;
                    continue;
                }
                if (deleteAction.delete(path)) deleted++;
            } catch (IOException | SecurityException exception) {
                failed++;
            }
        }
        return new DeleteOutcome(deleted, failed);
    }

    @FunctionalInterface
    interface DeleteAction { boolean delete(Path path) throws IOException; }
    record DeleteOutcome(long deletedFileCount, long failedFileCount) {}
}
