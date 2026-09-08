package dev.dhruv.jobsearch.notes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NoteFileService {

    private static final int MAX_NOTE_CHARACTERS = 100_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS");

    private final Path storageRoot;

    public NoteFileService(@Value("${app.notes.folder:../notes}") String storageFolder) {
        this.storageRoot = Path.of(storageFolder).toAbsolutePath().normalize();
    }

    public SavedNote save(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Write something before saving the note.");
        if (content.length() > MAX_NOTE_CHARACTERS) {
            throw new IllegalArgumentException("A scratch note must be 100,000 characters or fewer.");
        }
        OffsetDateTime savedAt = OffsetDateTime.now(ZoneId.systemDefault());
        String baseName = FILE_TIME.format(savedAt) + "_scratch-note";
        try {
            Files.createDirectories(storageRoot);
            Path target = uniqueTarget(baseName);
            Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new SavedNote(target.getFileName().toString(), "notes/" + target.getFileName(), savedAt);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save the note snapshot: " + exception.getMessage(), exception);
        }
    }

    private Path uniqueTarget(String baseName) {
        Path preferred = storageRoot.resolve(baseName + ".txt").normalize();
        if (!preferred.startsWith(storageRoot)) throw new IllegalStateException("The notes folder is not safe to write to.");
        if (!Files.exists(preferred)) return preferred;
        return storageRoot.resolve(baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".txt").normalize();
    }

    public record SavedNote(String filename, String storedPath, OffsetDateTime savedAt) {}
}
