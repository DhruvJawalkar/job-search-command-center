package dev.dhruv.jobsearch.summary;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.RecommendationFocus;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.SpecializationMode;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.DayPart;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PreferencesValues;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PreferencesView;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PriorityPreference;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.PrioritySource;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.ScheduleBlock;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferencesService.ScheduleSource;

@RestController
@RequestMapping("/api/v1/summary-preferences")
public class LocalSummaryPreferencesController {

    private final LocalSummaryPreferencesService service;

    public LocalSummaryPreferencesController(LocalSummaryPreferencesService service) { this.service = service; }

    @GetMapping
    SummaryPreferencesResponse get() { return SummaryPreferencesResponse.from(service.get()); }

    @PutMapping
    SummaryPreferencesResponse save(@Valid @RequestBody SummaryPreferencesRequest request) {
        return SummaryPreferencesResponse.from(service.save(new PreferencesValues(
                request.scheduleBlocks().stream().map(ScheduleBlockRequest::toValue).toList(),
                request.priorities().stream().map(PriorityPreferenceRequest::toValue).toList(),
                request.specializationMode(), request.fixedSpecializationDay(), request.recommendationFocus(),
                request.weeklyApplicationTarget(),
                request.showDailyPerspective(), request.showMarketLens(), request.showTechnologyWatch())));
    }

    public record SummaryPreferencesRequest(
            @NotNull @Size(min = 1, max = 12) List<@Valid ScheduleBlockRequest> scheduleBlocks,
            @NotNull @Size(min = 3, max = 3) List<@Valid PriorityPreferenceRequest> priorities,
            @NotNull SpecializationMode specializationMode,
            Integer fixedSpecializationDay,
            @NotNull RecommendationFocus recommendationFocus,
            @Min(1) @Max(100) int weeklyApplicationTarget,
            boolean showDailyPerspective,
            boolean showMarketLens,
            boolean showTechnologyWatch) {}

    public record ScheduleBlockRequest(
            @NotBlank @Size(max = 80) String id,
            @NotNull DayPart dayPart,
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String startTime,
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String endTime,
            @NotNull ScheduleSource source,
            @Size(max = 160) String customTitle,
            @Size(max = 500) String customDetail) {
        ScheduleBlock toValue() { return new ScheduleBlock(id, dayPart, startTime, endTime, source, customTitle, customDetail); }
    }

    public record PriorityPreferenceRequest(
            @NotNull PrioritySource source,
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 80) String timeLabel,
            @Size(max = 160) String customTitle,
            @Size(max = 500) String customDetail) {
        PriorityPreference toValue() { return new PriorityPreference(source, label, timeLabel, customTitle, customDetail); }
    }

    public record SummaryPreferencesResponse(List<ScheduleBlock> scheduleBlocks, List<PriorityPreference> priorities,
            SpecializationMode specializationMode, Integer fixedSpecializationDay, RecommendationFocus recommendationFocus,
            int weeklyApplicationTarget,
            boolean showDailyPerspective, boolean showMarketLens, boolean showTechnologyWatch,
            Instant updatedAt, long version) {
        static SummaryPreferencesResponse from(PreferencesView view) {
            return new SummaryPreferencesResponse(view.scheduleBlocks(), view.priorities(), view.specializationMode(),
                    view.fixedSpecializationDay(), view.recommendationFocus(), view.weeklyApplicationTarget(),
                    view.showDailyPerspective(), view.showMarketLens(), view.showTechnologyWatch(), view.updatedAt(), view.version());
        }
    }
}
