package dev.dhruv.jobsearch.skill;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "personal_skill_backlog")
public class PersonalSkillBacklog {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "skill_id") private CanonicalSkill skill;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private SkillProficiencyLevel currentLevel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private SkillProficiencyLevel targetLevel;
    @Column(nullable = false) private int priority;
    @Column(columnDefinition = "text") private String rationale;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private PersonalSkillStatus status;
    private LocalDate nextReviewOn;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected PersonalSkillBacklog() {}

    PersonalSkillBacklog(CanonicalSkill skill, SkillProficiencyLevel currentLevel,
            SkillProficiencyLevel targetLevel, int priority, String rationale,
            PersonalSkillStatus status, LocalDate nextReviewOn) {
        this.id = UUID.randomUUID();
        this.skill = skill;
        update(currentLevel, targetLevel, priority, rationale, status, nextReviewOn);
    }

    void update(SkillProficiencyLevel currentLevel, SkillProficiencyLevel targetLevel, int priority,
            String rationale, PersonalSkillStatus status, LocalDate nextReviewOn) {
        this.currentLevel = currentLevel == null ? SkillProficiencyLevel.NOT_ASSESSED : currentLevel;
        this.targetLevel = targetLevel == null ? SkillProficiencyLevel.WORKING_PROFICIENCY : targetLevel;
        if (this.currentLevel != SkillProficiencyLevel.NOT_ASSESSED
                && this.targetLevel.ordinal() < this.currentLevel.ordinal()) {
            throw new IllegalArgumentException("Target proficiency must not be below the current level.");
        }
        if (priority < 1 || priority > 5) throw new IllegalArgumentException("Priority must be between 1 and 5.");
        this.priority = priority;
        this.rationale = optional(rationale);
        this.status = status == null ? PersonalSkillStatus.BACKLOG : status;
        this.nextReviewOn = nextReviewOn;
    }

    void reassignSkill(CanonicalSkill target) { this.skill = target; }

    void absorb(PersonalSkillBacklog source) {
        if (currentLevel == SkillProficiencyLevel.NOT_ASSESSED) currentLevel = source.currentLevel;
        if (source.targetLevel.ordinal() > targetLevel.ordinal()) targetLevel = source.targetLevel;
        priority = Math.min(priority, source.priority);
        if (rationale == null) rationale = source.rationale;
        else if (source.rationale != null && !rationale.contains(source.rationale)) rationale += "\n" + source.rationale;
        if (nextReviewOn == null || (source.nextReviewOn != null && source.nextReviewOn.isBefore(nextReviewOn))) {
            nextReviewOn = source.nextReviewOn;
        }
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public CanonicalSkill getSkill() { return skill; }
    public SkillProficiencyLevel getCurrentLevel() { return currentLevel; }
    public SkillProficiencyLevel getTargetLevel() { return targetLevel; }
    public int getPriority() { return priority; }
    public String getRationale() { return rationale; }
    public PersonalSkillStatus getStatus() { return status; }
    public LocalDate getNextReviewOn() { return nextReviewOn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
