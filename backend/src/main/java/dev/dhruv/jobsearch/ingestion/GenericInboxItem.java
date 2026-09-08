package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "generic_inbox_item")
public class GenericInboxItem {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GenericInboxSourceType sourceType;

    @Column(length = 160)
    private String sourceLabel;

    @Column(length = 500)
    private String sourceFilename;

    @Column(length = 120)
    private String mediaType;

    @Column(nullable = false, unique = true, length = 64)
    private String contentHash;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false, length = 40)
    private String parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GenericInboxItemStatus status;

    @Column(nullable = false)
    private int candidateCount;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected GenericInboxItem() {
    }

    public GenericInboxItem(GenericInboxSourceType sourceType, String sourceLabel, String sourceFilename,
            String mediaType, String contentHash, String rawPayload, String parserVersion) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.sourceType = sourceType;
        this.sourceLabel = sourceLabel;
        this.sourceFilename = sourceFilename;
        this.mediaType = mediaType;
        this.contentHash = contentHash;
        this.rawPayload = rawPayload;
        this.parserVersion = parserVersion;
        this.status = GenericInboxItemStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void parsed(int candidateCount) {
        this.candidateCount = candidateCount;
        this.status = GenericInboxItemStatus.PARSED;
        this.errorMessage = null;
        touch();
    }

    public void updateReviewStatus(GenericInboxItemStatus status) {
        this.status = status;
        touch();
    }

    public void fail(String message) {
        this.status = GenericInboxItemStatus.FAILED;
        this.errorMessage = message;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public GenericInboxSourceType getSourceType() { return sourceType; }
    public String getSourceLabel() { return sourceLabel; }
    public String getSourceFilename() { return sourceFilename; }
    public String getMediaType() { return mediaType; }
    public String getContentHash() { return contentHash; }
    public String getRawPayload() { return rawPayload; }
    public String getParserVersion() { return parserVersion; }
    public GenericInboxItemStatus getStatus() { return status; }
    public int getCandidateCount() { return candidateCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
