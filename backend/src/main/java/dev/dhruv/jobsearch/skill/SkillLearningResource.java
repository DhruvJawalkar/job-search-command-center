package dev.dhruv.jobsearch.skill;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "skill_learning_resource")
public class SkillLearningResource {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "backlog_id") private PersonalSkillBacklog backlog;
    @Column(nullable = false, length = 240) private String title;
    @Column(length = 1000) private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private LearningResourceType resourceType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private LearningResourceStatus status;
    @Column(columnDefinition = "text") private String notes;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected SkillLearningResource() {}
    SkillLearningResource(PersonalSkillBacklog backlog, String title, String url, LearningResourceType type,
            LearningResourceStatus status, String notes) {
        this.id = UUID.randomUUID(); this.backlog = backlog; this.title = required(title);
        this.url = optional(url); this.resourceType = type == null ? LearningResourceType.OTHER : type;
        this.status = status == null ? LearningResourceStatus.PLANNED : status; this.notes = optional(notes);
    }
    void reassignBacklog(PersonalSkillBacklog target) { backlog = target; }
    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Resource title is required."); return value.trim(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getId() { return id; } public String getTitle() { return title; } public String getUrl() { return url; }
    public LearningResourceType getResourceType() { return resourceType; } public LearningResourceStatus getStatus() { return status; }
    public String getNotes() { return notes; }
}
