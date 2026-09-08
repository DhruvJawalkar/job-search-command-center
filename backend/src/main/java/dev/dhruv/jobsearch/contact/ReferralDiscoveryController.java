package dev.dhruv.jobsearch.contact;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/referral-candidates")
public class ReferralDiscoveryController {
    private final ReferralDiscoveryService service;
    public ReferralDiscoveryController(ReferralDiscoveryService service){this.service=service;}
    @GetMapping List<CandidateResponse> list(@RequestParam(required=false) UUID opportunityId){return service.list(opportunityId).stream().map(CandidateResponse::from).toList();}
    @PostMapping ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest r){
        var c=service.create(new ReferralDiscoveryService.NewCandidate(r.opportunityId,r.fullName,r.companyName,r.roleTitle,r.profileUrl,r.connectionDegree,
            r.discoveryChannel,r.relationshipStrength,r.mutualConnectionName,r.mutualConnectionUrl,r.currentCompanyMatch,r.formerCompanyMatch,
            r.roleRelevance,r.responsiveness,r.lastInteractionOn,r.notes));
        return ResponseEntity.created(URI.create("/api/v1/referral-candidates/"+c.getId())).body(CandidateResponse.from(c));
    }
    @PatchMapping("/{id}") CandidateResponse update(@PathVariable UUID id,@Valid @RequestBody CandidateRequest r){
        return CandidateResponse.from(service.update(id,new ReferralDiscoveryService.NewCandidate(r.opportunityId,r.fullName,r.companyName,r.roleTitle,r.profileUrl,r.connectionDegree,
            r.discoveryChannel,r.relationshipStrength,r.mutualConnectionName,r.mutualConnectionUrl,r.currentCompanyMatch,r.formerCompanyMatch,
            r.roleRelevance,r.responsiveness,r.lastInteractionOn,r.notes)));
    }
    @PostMapping("/{id}/outreach") CandidateResponse createOutreach(@PathVariable UUID id,@Valid @RequestBody OutreachRequest r){return CandidateResponse.from(service.createOutreach(id,new ReferralDiscoveryService.StartOutreach(r.channel,r.messageSummary,r.followUpAt,r.notes)));}
    @PatchMapping("/{id}/dismiss") CandidateResponse dismiss(@PathVariable UUID id){return CandidateResponse.from(service.dismiss(id));}

    public record CandidateRequest(@NotNull UUID opportunityId,@NotBlank String fullName,String companyName,String roleTitle,String profileUrl,
        ConnectionDegree connectionDegree,ReferralChannel discoveryChannel,RelationshipStrength relationshipStrength,String mutualConnectionName,
        String mutualConnectionUrl,boolean currentCompanyMatch,boolean formerCompanyMatch,@Min(1) @Max(5) int roleRelevance,
        @Min(1) @Max(5) int responsiveness,LocalDate lastInteractionOn,String notes){}
    public record OutreachRequest(String channel,String messageSummary,Instant followUpAt,String notes){}
    public record CandidateResponse(UUID id,UUID opportunityId,String companyName,String opportunityRole,String fullName,String candidateCompany,String roleTitle,
        String profileUrl,ConnectionDegree connectionDegree,ReferralChannel discoveryChannel,RelationshipStrength relationshipStrength,String mutualConnectionName,
        String mutualConnectionUrl,boolean currentCompanyMatch,boolean formerCompanyMatch,int roleRelevance,int responsiveness,LocalDate lastInteractionOn,
        ReferralCandidateStatus status,int pathScore,String pathStrength,String scoreExplanation,String notes,UUID contactId,UUID outreachId,Instant updatedAt){
        static CandidateResponse from(ReferralCandidate c){return new CandidateResponse(c.getId(),c.getOpportunity().getId(),c.getOpportunity().getCompanyName(),
            c.getOpportunity().getRoleTitle(),c.getFullName(),c.getCompanyName(),c.getRoleTitle(),c.getProfileUrl(),c.getConnectionDegree(),c.getDiscoveryChannel(),
            c.getRelationshipStrength(),c.getMutualConnectionName(),c.getMutualConnectionUrl(),c.isCurrentCompanyMatch(),c.isFormerCompanyMatch(),
            c.getRoleRelevance(),c.getResponsiveness(),c.getLastInteractionOn(),c.getStatus(),c.getPathScore(),c.getPathScore()>=70?"STRONG":c.getPathScore()>=45?"PROMISING":"EXPLORATORY",
            c.getScoreExplanation(),c.getNotes(),c.getContact()==null?null:c.getContact().getId(),c.getOutreach()==null?null:c.getOutreach().getId(),c.getUpdatedAt());}
    }
}
