package dev.dhruv.jobsearch.skill;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills/market-signals")
public class MarketSignalController {

    private final MarketSignalService service;

    public MarketSignalController(MarketSignalService service) {
        this.service = service;
    }

    @GetMapping
    MarketSignalService.MarketSignalOverview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) RoleFamily roleFamily,
            @RequestParam(required = false) SeniorityBand seniority,
            @RequestParam(required = false) String companySegment,
            @RequestParam(required = false) SkillStrength strength) {
        return service.overview(new MarketSignalService.MarketSignalQuery(
                from, to, roleFamily, seniority, companySegment, strength));
    }

    @GetMapping("/{skillId}/evidence")
    MarketSignalService.EvidenceDrilldown evidence(
            @PathVariable UUID skillId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) RoleFamily roleFamily,
            @RequestParam(required = false) SeniorityBand seniority,
            @RequestParam(required = false) String companySegment,
            @RequestParam(required = false) SkillStrength strength) {
        return service.evidence(skillId, new MarketSignalService.MarketSignalQuery(
                from, to, roleFamily, seniority, companySegment, strength));
    }
}
