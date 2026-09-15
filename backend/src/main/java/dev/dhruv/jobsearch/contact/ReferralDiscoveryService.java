package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class ReferralDiscoveryService {
    private final ReferralCandidateRepository candidates;
    private final JobOpportunityRepository opportunities;
    private final OutreachService outreachService;
    public ReferralDiscoveryService(ReferralCandidateRepository candidates,JobOpportunityRepository opportunities,OutreachService outreachService){
        this.candidates=candidates;this.opportunities=opportunities;this.outreachService=outreachService;
    }
    @Transactional(readOnly=true) public List<ReferralCandidate> list(UUID opportunityId){return opportunityId==null?candidates.findAllByOrderByPathScoreDescCreatedAtDesc():candidates.findByOpportunityIdOrderByPathScoreDescCreatedAtDesc(opportunityId);}
    @Transactional public ReferralCandidate create(NewCandidate c){
        var opportunity=opportunities.findById(c.opportunityId).orElseThrow(()->new NotFoundException("Opportunity "+c.opportunityId+" was not found."));
        var score=score(c);
        return candidates.save(new ReferralCandidate(opportunity,c.fullName,c.companyName,c.roleTitle,c.profileUrl,c.connectionDegree,c.discoveryChannel,
                c.relationshipStrength,c.mutualConnectionName,c.mutualConnectionUrl,c.currentCompanyMatch,c.formerCompanyMatch,c.roleRelevance,
                c.responsiveness,c.lastInteractionOn,score.value,score.explanation,c.notes));
    }
    @Transactional public ReferralCandidate update(UUID id,NewCandidate c){
        var candidate=get(id);
        if(!candidate.getOpportunity().getId().equals(c.opportunityId))throw new IllegalArgumentException("A referral candidate cannot be moved to another opportunity.");
        var score=score(c);
        candidate.updateProfile(c.fullName,c.companyName,c.roleTitle,c.profileUrl,c.connectionDegree,c.discoveryChannel,c.relationshipStrength,
                c.mutualConnectionName,c.mutualConnectionUrl,c.currentCompanyMatch,c.formerCompanyMatch,c.roleRelevance,c.responsiveness,
                c.lastInteractionOn,score.value,score.explanation,c.notes);
        return candidate;
    }
    @Transactional public ReferralCandidate createOutreach(UUID id,StartOutreach c){
        var candidate=get(id); if(candidate.getOutreach()!=null)return candidate;
        var contact=new OutreachService.NewContact(candidate.getFullName(),candidate.getCompanyName(),candidate.getRoleTitle(),candidate.getProfileUrl(),null,
                candidate.getRelationshipStrength(),candidate.getNotes());
        var activity=outreachService.create(new OutreachService.CreateOutreach(candidate.getOpportunity().getId(),null,contact,OutreachType.REFERRAL_REQUEST,
                OutreachStatus.PLANNED,c.channel,c.messageSummary,c.followUpAt,c.notes));
        candidate.markOutreach(activity.getContact(),activity); return candidate;
    }
    @Transactional public ReferralCandidate dismiss(UUID id){var candidate=get(id);candidate.dismiss();return candidate;}
    private ReferralCandidate get(UUID id){return candidates.findById(id).orElseThrow(()->new NotFoundException("Referral candidate "+id+" was not found."));}
    private Score score(NewCandidate c){
        int value=switch(c.connectionDegree==null?ConnectionDegree.OTHER:c.connectionDegree){case FIRST->30;case SECOND->18;case OTHER->5;};
        value+=switch(c.relationshipStrength==null?RelationshipStrength.COLD:c.relationshipStrength){case STRONG->30;case WARM->22;case FORMER_COLLEAGUE->25;case ACQUAINTANCE->10;case COLD->0;};
        if(c.currentCompanyMatch)value+=20;else if(c.formerCompanyMatch)value+=10;
        value+=(Math.max(1,Math.min(5,c.roleRelevance))-1)*5; value+=(Math.max(1,Math.min(5,c.responsiveness))-1)*2;
        if(c.lastInteractionOn!=null){long days=ChronoUnit.DAYS.between(c.lastInteractionOn,LocalDate.now());if(days<=180)value+=10;else if(days<=365)value+=5;}
        value=Math.min(100,value);
        var reasons=new ArrayList<String>();
        if(c.connectionDegree==ConnectionDegree.FIRST)reasons.add("1st-degree connection");
        if(c.connectionDegree==ConnectionDegree.SECOND)reasons.add(c.mutualConnectionName==null||c.mutualConnectionName.isBlank()?"2nd-degree introduction path":"mutual path through "+c.mutualConnectionName.trim());
        if(c.currentCompanyMatch)reasons.add("current employee");else if(c.formerCompanyMatch)reasons.add("former employee");
        if(c.relationshipStrength==RelationshipStrength.FORMER_COLLEAGUE)reasons.add("former colleague");else if(c.relationshipStrength==RelationshipStrength.WARM||c.relationshipStrength==RelationshipStrength.STRONG)reasons.add("warm relationship");
        if(c.roleRelevance>=4)reasons.add("close to the target team");
        String strength=value>=70?"Strong":value>=45?"Promising":"Exploratory";
        return new Score(value,strength+" path — "+(reasons.isEmpty()?"limited relationship evidence recorded":String.join(", ",reasons))+".");
    }
    private record Score(int value,String explanation){}
    public record NewCandidate(UUID opportunityId,String fullName,String companyName,String roleTitle,String profileUrl,ConnectionDegree connectionDegree,
        ReferralChannel discoveryChannel,RelationshipStrength relationshipStrength,String mutualConnectionName,String mutualConnectionUrl,
        boolean currentCompanyMatch,boolean formerCompanyMatch,int roleRelevance,int responsiveness,LocalDate lastInteractionOn,String notes){}
    public record StartOutreach(String channel,String messageSummary,Instant followUpAt,String notes){}
}
