package dev.dhruv.jobsearch.skill;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill_alias")
public class SkillAlias {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private CanonicalSkill skill;

    @Column(nullable = false, length = 160)
    private String alias;

    @Column(nullable = false, length = 160)
    private String normalizedAlias;

    @Column(nullable = false)
    private Instant createdAt;

    protected SkillAlias() {
    }

    SkillAlias(CanonicalSkill skill, String alias, String normalizedAlias) {
        this.id = UUID.randomUUID();
        this.skill = skill;
        this.alias = alias.trim();
        this.normalizedAlias = normalizedAlias;
    }

    void reassignTo(CanonicalSkill target) {
        this.skill = target;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CanonicalSkill getSkill() { return skill; }
    public String getAlias() { return alias; }
    public String getNormalizedAlias() { return normalizedAlias; }
    public Instant getCreatedAt() { return createdAt; }
}
