package dev.dhruv.jobsearch.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface GenericInboxItemRepository extends JpaRepository<GenericInboxItem, UUID> {
    Optional<GenericInboxItem> findByContentHash(String contentHash);
    List<GenericInboxItem> findTop30ByOrderByCreatedAtDesc();
}

interface GenericInboxCandidateRepository extends JpaRepository<GenericInboxCandidate, UUID> {
    List<GenericInboxCandidate> findByInboxItemIdOrderByRowNumber(UUID inboxItemId);
}

interface InboxDuplicateMatchRepository extends JpaRepository<InboxDuplicateMatch, UUID> {
    Optional<InboxDuplicateMatch> findByCandidateIdAndOpportunityId(UUID candidateId, UUID opportunityId);
    List<InboxDuplicateMatch> findByCandidateIdOrderByConfidenceDesc(UUID candidateId);
    List<InboxDuplicateMatch> findByCandidateIdAndStatusOrderByConfidenceDesc(UUID candidateId, DuplicateMatchStatus status);
}
