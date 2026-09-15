package dev.dhruv.jobsearch.summary;

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
@Table(name = "local_summary_preferences")
public class LocalSummaryPreferences {

    public static final UUID SINGLE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id private UUID id;
    @Column(nullable = false, columnDefinition = "text") private String scheduleJson;
    @Column(nullable = false, columnDefinition = "text") private String prioritiesJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SpecializationMode specializationMode;
    private Integer fixedSpecializationDay;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RecommendationFocus recommendationFocus;
    @Column(nullable = false) private int weeklyApplicationTarget;
    @Column(nullable = false) private boolean showDailyPerspective;
    @Column(nullable = false) private boolean showMarketLens;
    @Column(nullable = false) private boolean showTechnologyWatch;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected LocalSummaryPreferences() {
        id = SINGLE_USER_ID;
        specializationMode = SpecializationMode.ROTATING;
        recommendationFocus = RecommendationFocus.BALANCED;
        weeklyApplicationTarget = 10;
        showDailyPerspective = true;
        showMarketLens = true;
        showTechnologyWatch = true;
    }

    void update(String scheduleJson, String prioritiesJson, SpecializationMode specializationMode,
            Integer fixedSpecializationDay, RecommendationFocus recommendationFocus, int weeklyApplicationTarget,
            boolean showDailyPerspective, boolean showMarketLens, boolean showTechnologyWatch) {
        this.scheduleJson = scheduleJson;
        this.prioritiesJson = prioritiesJson;
        this.specializationMode = specializationMode;
        this.fixedSpecializationDay = specializationMode == SpecializationMode.FIXED ? fixedSpecializationDay : null;
        this.recommendationFocus = recommendationFocus;
        this.weeklyApplicationTarget = weeklyApplicationTarget;
        this.showDailyPerspective = showDailyPerspective;
        this.showMarketLens = showMarketLens;
        this.showTechnologyWatch = showTechnologyWatch;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getScheduleJson() { return scheduleJson; }
    public String getPrioritiesJson() { return prioritiesJson; }
    public SpecializationMode getSpecializationMode() { return specializationMode; }
    public Integer getFixedSpecializationDay() { return fixedSpecializationDay; }
    public RecommendationFocus getRecommendationFocus() { return recommendationFocus; }
    public int getWeeklyApplicationTarget() { return weeklyApplicationTarget; }
    public boolean isShowDailyPerspective() { return showDailyPerspective; }
    public boolean isShowMarketLens() { return showMarketLens; }
    public boolean isShowTechnologyWatch() { return showTechnologyWatch; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public enum SpecializationMode { ROTATING, FIXED }
    public enum RecommendationFocus { BALANCED, OPPORTUNITIES, NETWORK, PREPARATION }
}
