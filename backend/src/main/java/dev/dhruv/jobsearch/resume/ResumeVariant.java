package dev.dhruv.jobsearch.resume;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "resume_variant")
public class ResumeVariant {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 200)
    private String targetRole;

    @Column(nullable = false, length = 80)
    private String versionLabel;

    @Column(length = 1000)
    private String filePath;

    @Column(length = 128)
    private String contentHash;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ResumeVariant() {
    }

    public ResumeVariant(String name, String targetRole, String versionLabel, String filePath, String contentHash) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.targetRole = targetRole;
        this.versionLabel = versionLabel;
        this.filePath = filePath;
        this.contentHash = contentHash;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTargetRole() { return targetRole; }
    public String getVersionLabel() { return versionLabel; }
    public String getFilePath() { return filePath; }
    public String getContentHash() { return contentHash; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

