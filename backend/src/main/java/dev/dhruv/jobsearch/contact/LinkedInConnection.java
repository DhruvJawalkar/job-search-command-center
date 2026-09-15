package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "linkedin_connection")
public class LinkedInConnection {
    @Id private UUID id;
    @Column(nullable = false, length = 200) private String fullName;
    @Column(length = 240) private String companyName;
    @Column(length = 240) private String normalizedCompanyName;
    @Column(length = 500) private String roleTitle;
    @Column(length = 1500) private String profileUrl;
    @Column(nullable = false, length = 1500) private String normalizedProfileUrl;
    private LocalDate connectedOn;
    @Column(nullable = false) private int sourceRow;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_batch_id")
    private LinkedInConnectionImportBatch importBatch;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected LinkedInConnection() {}

    LinkedInConnection(String fullName, String companyName, String normalizedCompanyName, String roleTitle,
            String profileUrl, String normalizedProfileUrl, LocalDate connectedOn, int sourceRow,
            LinkedInConnectionImportBatch importBatch) {
        this.id = UUID.randomUUID();
        update(fullName, companyName, normalizedCompanyName, roleTitle, profileUrl, normalizedProfileUrl,
                connectedOn, sourceRow, importBatch);
    }

    void update(String fullName, String companyName, String normalizedCompanyName, String roleTitle,
            String profileUrl, String normalizedProfileUrl, LocalDate connectedOn, int sourceRow,
            LinkedInConnectionImportBatch importBatch) {
        this.fullName = fullName.trim();
        this.companyName = optional(companyName);
        this.normalizedCompanyName = optional(normalizedCompanyName);
        this.roleTitle = optional(roleTitle);
        this.profileUrl = optional(profileUrl);
        this.normalizedProfileUrl = normalizedProfileUrl;
        this.connectedOn = connectedOn;
        this.sourceRow = sourceRow;
        this.importBatch = importBatch;
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getCompanyName() { return companyName; }
    public String getNormalizedCompanyName() { return normalizedCompanyName; }
    public String getRoleTitle() { return roleTitle; }
    public String getProfileUrl() { return profileUrl; }
    public String getNormalizedProfileUrl() { return normalizedProfileUrl; }
    public LocalDate getConnectedOn() { return connectedOn; }
    public int getSourceRow() { return sourceRow; }
    public LinkedInConnectionImportBatch getImportBatch() { return importBatch; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
