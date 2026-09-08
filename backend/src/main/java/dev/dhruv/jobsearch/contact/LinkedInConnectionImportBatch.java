package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "linkedin_connection_import_batch")
public class LinkedInConnectionImportBatch {
    @Id private UUID id;
    @Column(nullable = false, length = 500) private String sourceFile;
    @Column(nullable = false, length = 64) private String contentHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Status status;
    @Column(nullable = false) private int rowsSeen;
    @Column(nullable = false) private int connectionsCreated;
    @Column(nullable = false) private int connectionsUpdated;
    @Column(nullable = false) private int rowsSkipped;
    @Column(columnDefinition = "text") private String errorMessage;
    @Column(nullable = false) private Instant startedAt;
    private Instant completedAt;

    protected LinkedInConnectionImportBatch() {}

    LinkedInConnectionImportBatch(String sourceFile, String contentHash) {
        this.id = UUID.randomUUID();
        this.sourceFile = sourceFile;
        this.contentHash = contentHash;
        this.status = Status.RUNNING;
        this.startedAt = Instant.now();
    }

    void complete(int rowsSeen, int created, int updated, int skipped) {
        this.rowsSeen = rowsSeen;
        this.connectionsCreated = created;
        this.connectionsUpdated = updated;
        this.rowsSkipped = skipped;
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    void fail(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSourceFile() { return sourceFile; }
    public String getContentHash() { return contentHash; }
    public Status getStatus() { return status; }
    public int getRowsSeen() { return rowsSeen; }
    public int getConnectionsCreated() { return connectionsCreated; }
    public int getConnectionsUpdated() { return connectionsUpdated; }
    public int getRowsSkipped() { return rowsSkipped; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public enum Status { RUNNING, COMPLETED, FAILED }
}
