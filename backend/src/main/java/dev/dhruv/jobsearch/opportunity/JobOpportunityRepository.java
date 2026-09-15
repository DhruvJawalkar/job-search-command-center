package dev.dhruv.jobsearch.opportunity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOpportunityRepository extends JpaRepository<JobOpportunity, UUID> {

    List<JobOpportunity> findAllByOrderByDiscoveredAtDesc();

    List<JobOpportunity> findByDemoFalseOrderByDiscoveredAtDesc();

    Optional<JobOpportunity> findByCanonicalUrl(String canonicalUrl);

    Optional<JobOpportunity> findBySourceNameIgnoreCaseAndSourceExternalId(String sourceName, String sourceExternalId);

    long countByStatus(OpportunityStatus status);

    long countByStatusAndDemoFalse(OpportunityStatus status);
}
