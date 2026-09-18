package dev.dhruv.jobsearch.privacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Reads a previewed file through directory handles that cannot be redirected by path replacement. */
@Component
class SafeWorkspaceFileReader {

    byte[] read(DataLifecycleInventoryService.PlannedFile file) {
        Path root = file.canonicalWorkspaceRoot();
        Path relative;
        try {
            relative = root.relativize(file.absolutePath()).normalize();
        } catch (IllegalArgumentException exception) {
            throw rejected(exception);
        }
        if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) throw rejected(null);

        Path anchor = root.getRoot();
        if (anchor == null) throw rejected(null);
        Path rootRelative = anchor.relativize(root);
        if (rootRelative.getNameCount() == 0) throw rejected(null);

        try (DirectoryStream<Path> opened = Files.newDirectoryStream(anchor)) {
            if (!(opened instanceof SecureDirectoryStream<?>)) {
                throw new IllegalStateException("This filesystem cannot safely open previewed workspace files without following replaceable paths. Export of workspace files is disabled on this runtime.");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> secureAnchor = (SecureDirectoryStream<Path>) opened;
            return openWorkspace(secureAnchor, rootRelative, 0, relative, file);
        } catch (IOException | SecurityException exception) {
            throw rejected(exception);
        }
    }

    private byte[] openWorkspace(SecureDirectoryStream<Path> directory, Path workspace, int index,
            Path relativeFile, DataLifecycleInventoryService.PlannedFile planned) throws IOException {
        try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(workspace.getName(index),
                LinkOption.NOFOLLOW_LINKS)) {
            if (index < workspace.getNameCount() - 1) {
                return openWorkspace(child, workspace, index + 1, relativeFile, planned);
            }
            return readFrom(child, relativeFile, 0, planned);
        }
    }

    private byte[] readFrom(SecureDirectoryStream<Path> directory, Path relative, int index,
            DataLifecycleInventoryService.PlannedFile planned) throws IOException {
        Path name = relative.getName(index);
        if (index < relative.getNameCount() - 1) {
            try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                return readFrom(child, relative, index + 1, planned);
            }
        }

        BasicFileAttributeView view = directory.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw rejected(null);
        BasicFileAttributes before = view.readAttributes();
        validate(before, planned);

        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = directory.newByteChannel(name, options)) {
            BasicFileAttributes opened = view.readAttributes();
            validate(opened, planned);
            requireSameFile(before, opened);

            if (planned.size() > Integer.MAX_VALUE) {
                throw new IllegalStateException("The previewed workspace file is too large for the local export archive.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) planned.size());
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            int read;
            while ((read = channel.read(buffer)) >= 0) {
                if (read == 0) continue;
                buffer.flip();
                output.write(buffer.array(), buffer.position(), buffer.remaining());
                buffer.clear();
            }

            BasicFileAttributes after = view.readAttributes();
            validate(after, planned);
            requireSameFile(opened, after);
            if (output.size() != planned.size()) throw rejected(null);
            return output.toByteArray();
        }
    }

    private void validate(BasicFileAttributes attributes, DataLifecycleInventoryService.PlannedFile planned) {
        String key = attributes.fileKey() == null ? null : attributes.fileKey().toString();
        if (!attributes.isRegularFile() || planned.fileKey() == null || !planned.fileKey().equals(key)
                || attributes.size() != planned.size()
                || attributes.lastModifiedTime().toMillis() != planned.modifiedAtMillis()) throw rejected(null);
    }

    private void requireSameFile(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.fileKey() == null || after.fileKey() == null
                || !before.fileKey().toString().equals(after.fileKey().toString())) throw rejected(null);
    }

    private IllegalStateException rejected(Throwable cause) {
        return new IllegalStateException(
                "A workspace file changed or could not be opened safely after preview. Create a new export preview.", cause);
    }
}
