package dev.dhruv.jobsearch.profile;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "local_user_profile")
public class LocalUserProfile {

    public static final UUID SINGLE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id private UUID id;
    @Column(length = 120) private String displayName;
    @Column(columnDefinition = "text") private String targetRoles;
    @Column(length = 120) private String targetLevel;
    @Column(columnDefinition = "text") private String preferredLocations;
    @Column(length = 500) private String preferredWorkModes;
    @Column(columnDefinition = "text") private String preferredCompanyTypes;
    @Column(length = 500) private String preferredCompanySizes;
    @Column(columnDefinition = "text") private String previousEmployers;
    @Column(columnDefinition = "text") private String careerGoals;
    @Column(columnDefinition = "text") private String cultureValues;
    @Column(columnDefinition = "text") private String includedTechnologies;
    @Column(columnDefinition = "text") private String excludedTechnologies;
    @Column(length = 20) private String dailySearchTime;
    @Column(length = 80) private String timeZone;
    @Column(nullable = false) private boolean onboardingCompleted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected LocalUserProfile() {
        this.id = SINGLE_USER_ID;
        this.onboardingCompleted = false;
    }

    public void update(ProfileValues values) {
        displayName = clean(values.displayName());
        targetRoles = clean(values.targetRoles());
        targetLevel = clean(values.targetLevel());
        preferredLocations = clean(values.preferredLocations());
        preferredWorkModes = clean(values.preferredWorkModes());
        preferredCompanyTypes = clean(values.preferredCompanyTypes());
        preferredCompanySizes = clean(values.preferredCompanySizes());
        previousEmployers = clean(values.previousEmployers());
        careerGoals = clean(values.careerGoals());
        cultureValues = clean(values.cultureValues());
        includedTechnologies = clean(values.includedTechnologies());
        excludedTechnologies = clean(values.excludedTechnologies());
        dailySearchTime = clean(values.dailySearchTime());
        timeZone = clean(values.timeZone());
        onboardingCompleted = values.onboardingCompleted();
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getTargetRoles() { return targetRoles; }
    public String getTargetLevel() { return targetLevel; }
    public String getPreferredLocations() { return preferredLocations; }
    public String getPreferredWorkModes() { return preferredWorkModes; }
    public String getPreferredCompanyTypes() { return preferredCompanyTypes; }
    public String getPreferredCompanySizes() { return preferredCompanySizes; }
    public String getPreviousEmployers() { return previousEmployers; }
    public String getCareerGoals() { return careerGoals; }
    public String getCultureValues() { return cultureValues; }
    public String getIncludedTechnologies() { return includedTechnologies; }
    public String getExcludedTechnologies() { return excludedTechnologies; }
    public String getDailySearchTime() { return dailySearchTime; }
    public String getTimeZone() { return timeZone; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public record ProfileValues(String displayName, String targetRoles, String targetLevel,
            String preferredLocations, String preferredWorkModes, String preferredCompanyTypes,
            String preferredCompanySizes, String previousEmployers, String careerGoals, String cultureValues,
            String includedTechnologies, String excludedTechnologies, String dailySearchTime, String timeZone,
            boolean onboardingCompleted) {}
}
