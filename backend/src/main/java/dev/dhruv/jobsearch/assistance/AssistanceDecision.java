package dev.dhruv.jobsearch.assistance;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "assistance_decision")
public class AssistanceDecision {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_id", nullable = false)
    private AssistanceRun run;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AssistanceDecisionType decisionType;
    @Column(columnDefinition = "text") private String selectedFields;
    @Column(columnDefinition = "text") private String note;
    @Column(nullable = false) private Instant createdAt;

    protected AssistanceDecision() {}

    public AssistanceDecision(AssistanceRun run, AssistanceDecisionType decisionType, String selectedFields, String note) {
        this.id = UUID.randomUUID();
        this.run = run;
        this.decisionType = decisionType;
        this.selectedFields = selectedFields;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AssistanceDecisionType getDecisionType() { return decisionType; }
    public String getSelectedFields() { return selectedFields; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
