package dev.dhruv.jobsearch.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationArtifactRepository extends JpaRepository<ApplicationArtifact, UUID> {
    List<ApplicationArtifact> findByApplicationIdOrderByArtifactTypeAsc(UUID applicationId);
}
