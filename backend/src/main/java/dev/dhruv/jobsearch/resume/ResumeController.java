package dev.dhruv.jobsearch.resume;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeVariantRepository repository;

    public ResumeController(ResumeVariantRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    List<ResumeResponse> list() {
        return repository.findByActiveTrueOrderByUpdatedAtDesc().stream().map(ResumeResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<ResumeResponse> create(@Valid @RequestBody CreateResumeRequest request) {
        ResumeVariant resume = repository.save(new ResumeVariant(
                request.name().trim(), request.targetRole().trim(), request.versionLabel().trim(),
                trimToNull(request.filePath()), trimToNull(request.contentHash())));
        return ResponseEntity.created(URI.create("/api/v1/resumes/" + resume.getId()))
                .body(ResumeResponse.from(resume));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateResumeRequest(
            @NotBlank String name,
            @NotBlank String targetRole,
            @NotBlank String versionLabel,
            String filePath,
            String contentHash) {
    }

    public record ResumeResponse(
            UUID id,
            String name,
            String targetRole,
            String versionLabel,
            String filePath,
            String contentHash,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

        static ResumeResponse from(ResumeVariant resume) {
            return new ResumeResponse(resume.getId(), resume.getName(), resume.getTargetRole(),
                    resume.getVersionLabel(), resume.getFilePath(), resume.getContentHash(), resume.isActive(),
                    resume.getCreatedAt(), resume.getUpdatedAt());
        }
    }
}
