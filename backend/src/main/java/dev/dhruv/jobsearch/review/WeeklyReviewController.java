package dev.dhruv.jobsearch.review;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/reviews/weekly")
public class WeeklyReviewController {

    private final WeeklyReviewService service;

    public WeeklyReviewController(WeeklyReviewService service) { this.service = service; }

    @GetMapping
    WeeklyReviewService.WeeklyOverview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return service.overview(weekStart);
    }

    @GetMapping("/monthly")
    WeeklyReviewService.MonthlyProgress monthly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return service.monthly(month);
    }

    @PostMapping
    ResponseEntity<WeeklyReviewService.CreateResult> generate(@Valid @RequestBody GenerateWeeklyReview request) {
        var result = service.generate(request.weekStart());
        if (result.replayed()) return ResponseEntity.ok(result);
        return ResponseEntity.created(URI.create("/api/v1/reviews/weekly/" + result.review().id())).body(result);
    }

    @PostMapping("/{id}/revisions")
    ResponseEntity<WeeklyReviewService.ReviewView> addRevision(@PathVariable UUID id,
            @Valid @RequestBody AddWeeklyReviewRevision request) {
        var result = service.addRevision(id, new WeeklyReviewService.RevisionCommand(request.wins(),
                request.challenges(), request.reflection(), request.nextWeekAdjustments(), request.nextWeekFocus()));
        return ResponseEntity.created(URI.create("/api/v1/reviews/weekly/" + id + "/revisions/"
                + result.revisions().getFirst().id())).body(result);
    }

    public record GenerateWeeklyReview(LocalDate weekStart) {}

    public record AddWeeklyReviewRevision(
            @Size(max = 10_000) String wins,
            @Size(max = 10_000) String challenges,
            @Size(max = 10_000) String reflection,
            @Size(max = 10_000) String nextWeekAdjustments,
            @Size(max = 5_000) String nextWeekFocus) {}
}
