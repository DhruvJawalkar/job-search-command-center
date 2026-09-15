package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "daily_priority_action")
public class DailyPriorityAction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunity_id")
    private JobOpportunity opportunity;

    @Column(nullable = false)
    private LocalDate actionDate;

    @Column(nullable = false)
    private int priorityRank;

    @Column(nullable = false, columnDefinition = "text")
    private String actionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DailyActionStatus status;

    @Column(nullable = false, length = 500)
    private String sourceFile;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected DailyPriorityAction() {
    }

    public DailyPriorityAction(ImportBatch importBatch, LocalDate actionDate, int priorityRank) {
        this.id = UUID.randomUUID();
        this.importBatch = importBatch;
        this.actionDate = actionDate;
        this.priorityRank = priorityRank;
        this.status = DailyActionStatus.TODO;
    }

    public void refresh(ImportBatch batch, JobOpportunity opportunity, String actionText, String sourceFile) {
        this.importBatch = batch;
        this.opportunity = opportunity;
        this.actionText = actionText;
        this.sourceFile = sourceFile;
    }

    public void changeStatus(DailyActionStatus status) {
        this.status = status;
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
    public ImportBatch getImportBatch() { return importBatch; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public LocalDate getActionDate() { return actionDate; }
    public int getPriorityRank() { return priorityRank; }
    public String getActionText() { return actionText; }
    public DailyActionStatus getStatus() { return status; }
    public String getSourceFile() { return sourceFile; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
