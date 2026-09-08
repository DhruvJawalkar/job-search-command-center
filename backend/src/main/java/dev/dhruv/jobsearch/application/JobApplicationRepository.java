package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findAllByOrderByUpdatedAtDesc();

    Optional<JobApplication> findByOpportunityId(UUID opportunityId);

    // Feed only needs these scalars, not resume/artifact/event entity graphs.
    @Query("select a.id as id, a.opportunity.id as opportunityId, a.stage as stage from JobApplication a")
    List<OpeningApplicationLink> findOpeningLinks();

    interface OpeningApplicationLink {
        UUID getId();
        UUID getOpportunityId();
        ApplicationStage getStage();
    }

    List<JobApplication> findByNextActionAtLessThanEqualOrderByNextActionAtAsc(Instant cutoff);

    long countByStage(ApplicationStage stage);
}
