package dev.dhruv.jobsearch.contact;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachActivityRepository extends JpaRepository<OutreachActivity, UUID> {
    List<OutreachActivity> findAllByOrderByFollowUpAtAscCreatedAtDesc();
    List<OutreachActivity> findByOpportunityIdOrderByCreatedAtDesc(UUID opportunityId);
}
