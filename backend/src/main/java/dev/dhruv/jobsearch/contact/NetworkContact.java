package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "network_contact")
public class NetworkContact {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 240)
    private String companyName;

    @Column(length = 240)
    private String roleTitle;

    @Column(length = 1500)
    private String profileUrl;

    @Column(length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RelationshipStrength relationshipStrength;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected NetworkContact() {
    }

    public NetworkContact(String fullName, String companyName, String roleTitle, String profileUrl,
            String email, RelationshipStrength relationshipStrength, String notes) {
        this.id = UUID.randomUUID();
        this.fullName = required(fullName, "Contact name");
        this.companyName = optional(companyName);
        this.roleTitle = optional(roleTitle);
        this.profileUrl = optional(profileUrl);
        this.email = optional(email);
        this.relationshipStrength = relationshipStrength == null ? RelationshipStrength.ACQUAINTANCE : relationshipStrength;
        this.notes = optional(notes);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
    public String getFullName() { return fullName; }
    public String getCompanyName() { return companyName; }
    public String getRoleTitle() { return roleTitle; }
    public String getProfileUrl() { return profileUrl; }
    public String getEmail() { return email; }
    public RelationshipStrength getRelationshipStrength() { return relationshipStrength; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
