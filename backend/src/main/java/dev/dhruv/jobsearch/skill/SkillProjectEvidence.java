package dev.dhruv.jobsearch.skill;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "skill_project_evidence")
public class SkillProjectEvidence {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "backlog_id") private PersonalSkillBacklog backlog;
    @Column(nullable = false, length = 240) private String title;
    @Column(length = 1000) private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ProjectEvidenceType evidenceType;
    @Column(columnDefinition = "text") private String description;
    @Column(columnDefinition = "text") private String outcome;
    private LocalDate completedOn;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected SkillProjectEvidence() {}
    SkillProjectEvidence(PersonalSkillBacklog backlog, String title, String url, ProjectEvidenceType type,
            String description, String outcome, LocalDate completedOn) {
        this.id = UUID.randomUUID(); this.backlog = backlog; this.title = required(title); this.url = optional(url);
        this.evidenceType = type == null ? ProjectEvidenceType.PROJECT : type; this.description = optional(description);
        this.outcome = optional(outcome); this.completedOn = completedOn;
    }
    void reassignBacklog(PersonalSkillBacklog target) { backlog = target; }
    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Project evidence title is required."); return value.trim(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getId() { return id; } public String getTitle() { return title; } public String getUrl() { return url; }
    public ProjectEvidenceType getEvidenceType() { return evidenceType; } public String getDescription() { return description; }
    public String getOutcome() { return outcome; } public LocalDate getCompletedOn() { return completedOn; }
}
