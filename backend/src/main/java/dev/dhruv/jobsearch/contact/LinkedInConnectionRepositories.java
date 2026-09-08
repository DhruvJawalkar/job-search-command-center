package dev.dhruv.jobsearch.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LinkedInConnectionRepository extends JpaRepository<LinkedInConnection, UUID> {
    Optional<LinkedInConnection> findByNormalizedProfileUrl(String normalizedProfileUrl);
    List<LinkedInConnection> findByNormalizedCompanyNameOrderByConnectedOnDescFullNameAsc(String companyName);
}

interface LinkedInConnectionImportBatchRepository extends JpaRepository<LinkedInConnectionImportBatch, UUID> {
    Optional<LinkedInConnectionImportBatch> findByContentHash(String contentHash);
    List<LinkedInConnectionImportBatch> findTop10ByOrderByStartedAtDesc();
    Optional<LinkedInConnectionImportBatch> findFirstByOrderByStartedAtDesc();
}
