package dev.dhruv.jobsearch.summary;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.RecommendationFocus;
import dev.dhruv.jobsearch.summary.LocalSummaryPreferences.SpecializationMode;

@Service
public class LocalSummaryPreferencesService {

    private static final TypeReference<List<ScheduleBlock>> SCHEDULE_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PriorityPreference>> PRIORITIES_TYPE = new TypeReference<>() {};

    private final LocalSummaryPreferencesRepository repository;
    private final ObjectMapper objectMapper;

    public LocalSummaryPreferencesService(LocalSummaryPreferencesRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PreferencesView get() {
        return repository.findById(LocalSummaryPreferences.SINGLE_USER_ID)
                .map(this::toView)
                .orElseGet(LocalSummaryPreferencesService::defaults);
    }

    @Transactional
    public PreferencesView save(PreferencesValues values) {
        validate(values);
        LocalSummaryPreferences preferences = repository.findById(LocalSummaryPreferences.SINGLE_USER_ID)
                .orElseGet(LocalSummaryPreferences::new);
        preferences.update(write(values.scheduleBlocks()), write(values.priorities()), values.specializationMode(),
                values.fixedSpecializationDay(), values.recommendationFocus(), values.weeklyApplicationTarget(), values.showDailyPerspective(),
                values.showMarketLens(), values.showTechnologyWatch());
        return toView(repository.save(preferences));
    }

    private PreferencesView toView(LocalSummaryPreferences preferences) {
        List<ScheduleBlock> schedule = read(preferences.getScheduleJson(), SCHEDULE_TYPE, defaultSchedule());
        List<PriorityPreference> priorities = read(preferences.getPrioritiesJson(), PRIORITIES_TYPE, defaultPriorities());
        return new PreferencesView(schedule, priorities, preferences.getSpecializationMode(),
                preferences.getFixedSpecializationDay(), preferences.getRecommendationFocus(),
                preferences.getWeeklyApplicationTarget(),
                preferences.isShowDailyPerspective(), preferences.isShowMarketLens(),
                preferences.isShowTechnologyWatch(), preferences.getUpdatedAt(), preferences.getVersion());
    }

    private void validate(PreferencesValues values) {
        if (values.scheduleBlocks() == null || values.scheduleBlocks().isEmpty() || values.scheduleBlocks().size() > 12)
            throw new IllegalArgumentException("Keep between 1 and 12 schedule blocks.");
        if (values.priorities() == null || values.priorities().size() != 3)
            throw new IllegalArgumentException("Configure exactly three Summary priorities.");
        if (values.specializationMode() == null || values.recommendationFocus() == null)
            throw new IllegalArgumentException("Choose specialization and recommendation modes.");
        if (values.weeklyApplicationTarget() < 1 || values.weeklyApplicationTarget() > 100)
            throw new IllegalArgumentException("Weekly application target must be between 1 and 100.");
        if (values.specializationMode() == SpecializationMode.FIXED
                && (values.fixedSpecializationDay() == null || values.fixedSpecializationDay() < 0 || values.fixedSpecializationDay() > 6))
            throw new IllegalArgumentException("Choose a specialization for fixed mode.");
        var ids = new HashSet<String>();
        for (ScheduleBlock block : values.scheduleBlocks()) {
            if (block == null || blank(block.id()) || block.dayPart() == null || block.source() == null)
                throw new IllegalArgumentException("Every schedule block needs an ID, daypart, and source.");
            if (!ids.add(block.id())) throw new IllegalArgumentException("Schedule block IDs must be unique.");
            LocalTime start = parseTime(block.startTime(), "Schedule start time");
            LocalTime end = parseTime(block.endTime(), "Schedule end time");
            if (!end.isAfter(start)) throw new IllegalArgumentException("Each schedule block must end after it starts.");
            if (block.source() == ScheduleSource.CUSTOM && blank(block.customTitle()))
                throw new IllegalArgumentException("Custom schedule blocks need a title.");
        }
        for (DayPart dayPart : DayPart.values()) {
            var blocks = values.scheduleBlocks().stream().filter(block -> block.dayPart() == dayPart)
                    .sorted(Comparator.comparing(block -> parseTime(block.startTime(), "Schedule start time"))).toList();
            for (int index = 1; index < blocks.size(); index++) {
                LocalTime previousEnd = parseTime(blocks.get(index - 1).endTime(), "Schedule end time");
                LocalTime currentStart = parseTime(blocks.get(index).startTime(), "Schedule start time");
                if (currentStart.isBefore(previousEnd))
                    throw new IllegalArgumentException("Schedule blocks in the same daypart cannot overlap.");
            }
        }
        for (PriorityPreference priority : values.priorities()) {
            if (priority == null || priority.source() == null || blank(priority.label()) || blank(priority.timeLabel()))
                throw new IllegalArgumentException("Each priority needs a source, label, and time label.");
            if (priority.source() == PrioritySource.CUSTOM && blank(priority.customTitle()))
                throw new IllegalArgumentException("Custom priorities need a title.");
        }
    }

    private LocalTime parseTime(String value, String label) {
        try { return LocalTime.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(label + " must use HH:mm."); }
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type, List<T> fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return objectMapper.readValue(json, type); }
        catch (JacksonException exception) { throw new IllegalStateException("Saved Summary preferences could not be read.", exception); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Summary preferences could not be saved.", exception); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public static PreferencesView defaults() {
        return new PreferencesView(defaultSchedule(), defaultPriorities(), SpecializationMode.ROTATING, 1,
                RecommendationFocus.BALANCED, 10, true, true, true, null, 0);
    }

    private static List<ScheduleBlock> defaultSchedule() {
        return List.of(
                new ScheduleBlock("coding", DayPart.MORNING, "05:00", "07:30", ScheduleSource.CODING, null, null),
                new ScheduleBlock("breakfast", DayPart.MORNING, "07:30", "08:30", ScheduleSource.BREAK, "Breakfast and reset", null),
                new ScheduleBlock("opportunity-primary", DayPart.MORNING, "08:30", "10:50", ScheduleSource.OPPORTUNITY, null, null),
                new ScheduleBlock("outreach-morning", DayPart.MORNING, "11:00", "11:30", ScheduleSource.OUTREACH, null, null),
                new ScheduleBlock("lunch", DayPart.MORNING, "11:30", "12:30", ScheduleSource.BREAK, "Lunch break", null),
                new ScheduleBlock("specialization-primary", DayPart.AFTERNOON, "12:30", "14:25", ScheduleSource.SPECIALIZATION, null, null),
                new ScheduleBlock("opportunity-secondary", DayPart.AFTERNOON, "14:30", "15:00", ScheduleSource.OPPORTUNITY, null, null),
                new ScheduleBlock("short-break", DayPart.AFTERNOON, "15:00", "15:15", ScheduleSource.BREAK, "Short break", null),
                new ScheduleBlock("outreach-afternoon", DayPart.AFTERNOON, "15:15", "15:45", ScheduleSource.OUTREACH, null, null),
                new ScheduleBlock("specialization-secondary", DayPart.AFTERNOON, "15:45", "17:00", ScheduleSource.SPECIALIZATION, null, null),
                new ScheduleBlock("end-of-day", DayPart.EVENING, "18:30", "19:30", ScheduleSource.END_OF_DAY, null, null));
    }

    private static List<PriorityPreference> defaultPriorities() {
        return List.of(
                new PriorityPreference(PrioritySource.CODING, "Daily anchor", "5:00–7:30 AM", null, null),
                new PriorityPreference(PrioritySource.OPPORTUNITY, "Daily anchor", "8:30–10:50 AM", null, null),
                new PriorityPreference(PrioritySource.SPECIALIZATION, "Specialization", "12:30–2:25 PM", null, null));
    }

    public record PreferencesValues(List<ScheduleBlock> scheduleBlocks, List<PriorityPreference> priorities,
            SpecializationMode specializationMode, Integer fixedSpecializationDay, RecommendationFocus recommendationFocus,
            int weeklyApplicationTarget,
            boolean showDailyPerspective, boolean showMarketLens, boolean showTechnologyWatch) {}
    public record PreferencesView(List<ScheduleBlock> scheduleBlocks, List<PriorityPreference> priorities,
            SpecializationMode specializationMode, Integer fixedSpecializationDay, RecommendationFocus recommendationFocus,
            int weeklyApplicationTarget,
            boolean showDailyPerspective, boolean showMarketLens, boolean showTechnologyWatch,
            java.time.Instant updatedAt, long version) {}
    public record ScheduleBlock(String id, DayPart dayPart, String startTime, String endTime,
            ScheduleSource source, String customTitle, String customDetail) {}
    public record PriorityPreference(PrioritySource source, String label, String timeLabel,
            String customTitle, String customDetail) {}
    public enum DayPart { MORNING, AFTERNOON, EVENING }
    public enum ScheduleSource { CODING, OPPORTUNITY, OUTREACH, SPECIALIZATION, APPLICATION_FOLLOW_UP, BREAK, CUSTOM, END_OF_DAY }
    public enum PrioritySource { CODING, OPPORTUNITY, OUTREACH, SPECIALIZATION, APPLICATION_FOLLOW_UP, CUSTOM }
}
