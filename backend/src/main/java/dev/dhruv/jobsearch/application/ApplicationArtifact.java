package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "application_artifact", uniqueConstraints = {
        @UniqueConstraint(name = "uk_application_artifact_type", columnNames = {"application_id", "artifact_type"}),
        @UniqueConstraint(name = "uk_application_artifact_path", columnNames = "stored_relative_path")
})
public class ApplicationArtifact {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplicationArtifactType artifactType;

    @Column(length = 500)
    private String originalFilename;

    @Column(nullable = false, length = 1200)
    private String storedRelativePath;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 160)
    private String mediaType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private Instant createdAt;

    protected ApplicationArtifact() {}

    public ApplicationArtifact(JobApplication application, ApplicationArtifactType artifactType,
            String originalFilename, String storedRelativePath, String contentHash, String mediaType,
            long sizeBytes) {
        this.id = UUID.randomUUID();
        this.application = application;
        this.artifactType = artifactType;
        this.originalFilename = originalFilename;
        this.storedRelativePath = storedRelativePath;
        this.contentHash = contentHash;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public JobApplication getApplication() { return application; }
    public ApplicationArtifactType getArtifactType() { return artifactType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredRelativePath() { return storedRelativePath; }
    public String getContentHash() { return contentHash; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
