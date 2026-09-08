package dev.dhruv.jobsearch.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityIntelligenceController {

    private final OpportunityIntelligenceService service;

    public OpportunityIntelligenceController(OpportunityIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/intelligence")
    OpportunityIntelligenceService.OpportunityFeed feed(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String recommendation,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String resume,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate observedOn) {
        return service.feed(query, recommendation, location, resume, minScore, observedOn, archived);
    }

    @GetMapping("/{id}/intelligence")
    OpportunityIntelligenceService.OpeningDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }
}
