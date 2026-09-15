package dev.dhruv.jobsearch.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCandidateRepository extends JpaRepository<ReferralCandidate,UUID> {
    List<ReferralCandidate> findAllByOrderByPathScoreDescCreatedAtDesc();
    List<ReferralCandidate> findByOpportunityIdOrderByPathScoreDescCreatedAtDesc(UUID opportunityId);
    Optional<ReferralCandidate> findByOpportunityIdAndProfileUrl(UUID opportunityId, String profileUrl);
}
