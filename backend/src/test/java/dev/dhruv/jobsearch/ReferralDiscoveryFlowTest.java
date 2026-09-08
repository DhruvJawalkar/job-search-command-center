package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.contact.*;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest @Transactional
class ReferralDiscoveryFlowTest {
    @Autowired OpportunityService opportunityService;
    @Autowired ReferralDiscoveryService referralService;

    @Test void ranksAReferralPathAndConvertsItToTrackedOutreach(){
        var opportunity=opportunityService.create(new OpportunityService.CreateOpportunity("Path Company","Senior Platform Engineer","Hyderabad",WorkMode.HYBRID,"Test","https://paths.example/jobs/1","Platform role",Instant.now()));
        var candidate=referralService.create(new ReferralDiscoveryService.NewCandidate(opportunity.getId(),"Asha Rao","Path Company","Staff Engineer",
            "https://linkedin.com/in/asha",ConnectionDegree.FIRST,ReferralChannel.FORMER_COLLEAGUE,RelationshipStrength.FORMER_COLLEAGUE,
            null,null,true,false,5,4,LocalDate.now().minusDays(30),"Worked together on a production platform"));

        assertThat(candidate.getPathScore()).isGreaterThanOrEqualTo(70);
        assertThat(candidate.getScoreExplanation()).contains("Strong path","1st-degree connection","current employee");

        var updated=referralService.update(candidate.getId(),new ReferralDiscoveryService.NewCandidate(opportunity.getId(),"Asha Rao","Path Company","Staff Engineer",
            "https://linkedin.com/in/asha",ConnectionDegree.SECOND,ReferralChannel.LINKEDIN,RelationshipStrength.COLD,
            "Mutual Teammate",null,true,false,5,3,null,"Connection request drafted"));
        assertThat(updated.getConnectionDegree()).isEqualTo(ConnectionDegree.SECOND);
        assertThat(updated.getMutualConnectionName()).isEqualTo("Mutual Teammate");
        assertThat(updated.getScoreExplanation()).contains("mutual path through Mutual Teammate","close to the target team");
        assertThat(updated.getNotes()).isEqualTo("Connection request drafted");

        var converted=referralService.createOutreach(candidate.getId(),new ReferralDiscoveryService.StartOutreach("LinkedIn","Ask for role context",Instant.now().plus(2,ChronoUnit.DAYS),"Personalize before sending"));
        assertThat(converted.getStatus()).isEqualTo(ReferralCandidateStatus.OUTREACH_CREATED);
        assertThat(converted.getContact().getFullName()).isEqualTo("Asha Rao");
        assertThat(converted.getOutreach().getStatus()).isEqualTo(OutreachStatus.PLANNED);
        assertThat(converted.getOutreach().getOpportunity().getId()).isEqualTo(opportunity.getId());
    }
}
