package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.RecommendationFocus;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.SpecializationMode;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.DayPart;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PreferencesValues;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PriorityPreference;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PrioritySource;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.ScheduleBlock;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.ScheduleSource;

@SpringBootTest
@Transactional
class LocalSummaryPreferencesFlowTest {

    @Autowired LocalSummaryPreferencesService service;

    @Test
    void providesDefaultsAndPersistsLocalSummaryConfiguration() {
        var defaults = service.get();
        assertThat(defaults.scheduleBlocks()).hasSize(11);
        assertThat(defaults.priorities()).hasSize(3);
        assertThat(defaults.specializationMode()).isEqualTo(SpecializationMode.ROTATING);
        assertThat(defaults.weeklyApplicationTarget()).isEqualTo(10);

        var saved = service.save(new PreferencesValues(
                List.of(
                        new ScheduleBlock("focus", DayPart.MORNING, "09:00", "10:00",
                                ScheduleSource.SPECIALIZATION, null, null),
                        new ScheduleBlock("break", DayPart.AFTERNOON, "13:00", "13:30",
                                ScheduleSource.BREAK, "Lunch", "Step away")),
                List.of(
                        new PriorityPreference(PrioritySource.OPPORTUNITY, "First", "9:00–10:00", null, null),
                        new PriorityPreference(PrioritySource.SPECIALIZATION, "Second", "10:00–11:00", null, null),
                        new PriorityPreference(PrioritySource.CUSTOM, "Third", "4:00–5:00", "Portfolio evidence", "Publish one local draft")),
                SpecializationMode.FIXED, 5, RecommendationFocus.PREPARATION,
                12,
                false, true, false));

        assertThat(saved.scheduleBlocks()).hasSize(2);
        assertThat(saved.priorities().get(2).customTitle()).isEqualTo("Portfolio evidence");
        assertThat(saved.fixedSpecializationDay()).isEqualTo(5);
        assertThat(saved.recommendationFocus()).isEqualTo(RecommendationFocus.PREPARATION);
        assertThat(saved.weeklyApplicationTarget()).isEqualTo(12);
        assertThat(saved.showDailyPerspective()).isFalse();
        assertThat(saved.version()).isZero();

        var reloaded = service.get();
        assertThat(reloaded.scheduleBlocks().getFirst().startTime()).isEqualTo("09:00");
        assertThat(reloaded.showTechnologyWatch()).isFalse();
    }

    @Test
    void rejectsOverlappingBlocksWithinTheSameDaypart() {
        assertThatThrownBy(() -> service.save(new PreferencesValues(
                List.of(
                        new ScheduleBlock("first", DayPart.MORNING, "09:00", "10:30", ScheduleSource.CODING, null, null),
                        new ScheduleBlock("second", DayPart.MORNING, "10:00", "11:00", ScheduleSource.OPPORTUNITY, null, null)),
                List.of(
                        new PriorityPreference(PrioritySource.CODING, "First", "9:00", null, null),
                        new PriorityPreference(PrioritySource.OPPORTUNITY, "Second", "10:00", null, null),
                        new PriorityPreference(PrioritySource.SPECIALIZATION, "Third", "11:00", null, null)),
                SpecializationMode.ROTATING, null, RecommendationFocus.BALANCED,
                10,
                true, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot overlap");
    }
}
