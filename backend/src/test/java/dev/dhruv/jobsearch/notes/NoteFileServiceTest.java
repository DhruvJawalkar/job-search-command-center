package dev.dhruv.jobsearch.notes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoteFileServiceTest {

    @TempDir Path tempDirectory;

    @Test
    void savesAnExactUtf8SnapshotWithACollisionSafeName() throws Exception {
        NoteFileService service = new NoteFileService(tempDirectory.toString());

        NoteFileService.SavedNote result = service.save("Interview ideas\n- clarify the reliability story");

        Path saved = tempDirectory.resolve(result.filename());
        assertThat(result.filename()).matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}_\\d{3}_scratch-note\\.txt");
        assertThat(result.storedPath()).isEqualTo("notes/" + result.filename());
        assertThat(Files.readString(saved)).isEqualTo("Interview ideas\n- clarify the reliability story");
    }
}
