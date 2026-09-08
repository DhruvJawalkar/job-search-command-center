package dev.dhruv.jobsearch.assistance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssistanceRunRepository extends JpaRepository<AssistanceRun, UUID> {
    Optional<AssistanceRun> findByUseCaseAndTargetIdAndInputHashAndProviderAndModelAndPromptVersionAndSchemaVersion(
            AssistanceUseCase useCase, UUID targetId, String inputHash, String provider, String model,
            String promptVersion, String schemaVersion);
    List<AssistanceRun> findByUseCaseAndTargetIdOrderByCreatedAtDesc(AssistanceUseCase useCase, UUID targetId);
}

interface AssistanceDecisionRepository extends JpaRepository<AssistanceDecision, UUID> {
    List<AssistanceDecision> findByRunIdOrderByCreatedAtDesc(UUID runId);
}
