package dev.dhruv.jobsearch.skill;

import java.time.Instant;
import java.util.UUID;

import dev.dhruv.jobsearch.preparation.PreparationItem;
import jakarta.persistence.*;

@Entity
@Table(name = "personal_skill_preparation_link")
public class PersonalSkillPreparationLink {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "backlog_id") private PersonalSkillBacklog backlog;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "prep_item_id") private PreparationItem prepItem;
    @Column(columnDefinition = "text") private String linkNote;
    @Column(nullable = false) private Instant createdAt;

    protected PersonalSkillPreparationLink() {}
    PersonalSkillPreparationLink(PersonalSkillBacklog backlog, PreparationItem prepItem, String linkNote) {
        this.id = UUID.randomUUID(); this.backlog = backlog; this.prepItem = prepItem;
        this.linkNote = optional(linkNote);
    }
    void reassignBacklog(PersonalSkillBacklog target) { backlog = target; }
    @PrePersist void prePersist() { createdAt = Instant.now(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getId() { return id; }
    public PersonalSkillBacklog getBacklog() { return backlog; }
    public PreparationItem getPrepItem() { return prepItem; }
    public String getLinkNote() { return linkNote; }
}
