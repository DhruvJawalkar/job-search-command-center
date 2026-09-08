package dev.dhruv.jobsearch.notes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteFileService notes;

    public NoteController(NoteFileService notes) {
        this.notes = notes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NoteFileService.SavedNote save(@Valid @RequestBody SaveNoteRequest request) {
        return notes.save(request.content());
    }

    public record SaveNoteRequest(@NotBlank @Size(max = 100_000) String content) {}
}
