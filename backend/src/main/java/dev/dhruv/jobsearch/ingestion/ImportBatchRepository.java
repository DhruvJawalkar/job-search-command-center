package dev.dhruv.jobsearch.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByContentHash(String contentHash);

    List<ImportBatch> findTop20ByOrderByStartedAtDesc();
}
