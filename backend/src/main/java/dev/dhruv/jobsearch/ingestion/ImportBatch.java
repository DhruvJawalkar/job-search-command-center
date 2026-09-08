package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String sourceType;

    @Column(nullable = false, length = 500)
    private String sourceFile;

    @Column(length = 500)
    private String actionSourceFile;

    @Column(nullable = false)
    private LocalDate sourceDate;

    @Column(nullable = false, unique = true, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportStatus status;

    @Column(nullable = false)
    private int rowsSeen;

    @Column(nullable = false)
    private int opportunitiesCreated;

    @Column(nullable = false)
    private int opportunitiesUpdated;

    @Column(nullable = false)
    private int observationsCreated;

    @Column(nullable = false)
    private int observationsUpdated;

    @Column(nullable = false)
    private int actionsCreated;

    @Column(nullable = false)
    private int actionsUpdated;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    protected ImportBatch() {
    }

    public ImportBatch(String sourceFile, String actionSourceFile, LocalDate sourceDate, String contentHash) {
        this.id = UUID.randomUUID();
        this.sourceType = "DAILY_HIGH_FIT_EXCEL";
        this.sourceFile = sourceFile;
        this.actionSourceFile = actionSourceFile;
        this.sourceDate = sourceDate;
        this.contentHash = contentHash;
        this.status = ImportStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete(ImportCounts counts) {
        this.rowsSeen = counts.rowsSeen();
        this.opportunitiesCreated = counts.opportunitiesCreated();
        this.opportunitiesUpdated = counts.opportunitiesUpdated();
        this.observationsCreated = counts.observationsCreated();
        this.observationsUpdated = counts.observationsUpdated();
        this.actionsCreated = counts.actionsCreated();
        this.actionsUpdated = counts.actionsUpdated();
        this.status = ImportStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = ImportStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSourceFile() { return sourceFile; }
    public String getActionSourceFile() { return actionSourceFile; }
    public LocalDate getSourceDate() { return sourceDate; }
    public String getContentHash() { return contentHash; }
    public ImportStatus getStatus() { return status; }
    public int getRowsSeen() { return rowsSeen; }
    public int getOpportunitiesCreated() { return opportunitiesCreated; }
    public int getOpportunitiesUpdated() { return opportunitiesUpdated; }
    public int getObservationsCreated() { return observationsCreated; }
    public int getObservationsUpdated() { return observationsUpdated; }
    public int getActionsCreated() { return actionsCreated; }
    public int getActionsUpdated() { return actionsUpdated; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public record ImportCounts(int rowsSeen, int opportunitiesCreated, int opportunitiesUpdated,
            int observationsCreated, int observationsUpdated, int actionsCreated, int actionsUpdated) {
    }
}
