package dev.dhruv.jobsearch.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CanonicalSkillRepository extends JpaRepository<CanonicalSkill, UUID> {
    Optional<CanonicalSkill> findByNormalizedName(String normalizedName);
    List<CanonicalSkill> findByActiveTrueOrderByNameAsc();
}

interface SkillAliasRepository extends JpaRepository<SkillAlias, UUID> {
    Optional<SkillAlias> findByNormalizedAlias(String normalizedAlias);
    List<SkillAlias> findBySkillId(UUID skillId);
    List<SkillAlias> findAllByOrderByAliasAsc();
}

interface JobDescriptionSnapshotRepository extends JpaRepository<JobDescriptionSnapshot, UUID> {
    Optional<JobDescriptionSnapshot> findByOpportunityIdAndContentHash(UUID opportunityId, String contentHash);
    Optional<JobDescriptionSnapshot> findFirstByOpportunityIdAndSourceTypeOrderByCapturedAtDesc(
            UUID opportunityId, SnapshotSourceType sourceType);
    List<JobDescriptionSnapshot> findByOpportunityIdOrderByCapturedAtDesc(UUID opportunityId);
}

interface JobSkillObservationRepository extends JpaRepository<JobSkillObservation, UUID> {
    Optional<JobSkillObservation> findBySnapshotIdAndSkillIdAndEvidenceFingerprint(
            UUID snapshotId, UUID skillId, String evidenceFingerprint);
    List<JobSkillObservation> findBySkillId(UUID skillId);
    List<JobSkillObservation> findByReviewStatusOrderByUpdatedAtDesc(SkillReviewStatus reviewStatus);
    List<JobSkillObservation> findAllByOrderByUpdatedAtDesc();
}

interface PersonalSkillBacklogRepository extends JpaRepository<PersonalSkillBacklog, UUID> {
    Optional<PersonalSkillBacklog> findBySkillId(UUID skillId);
    List<PersonalSkillBacklog> findAllByOrderByPriorityAscCreatedAtAsc();
}

interface PersonalSkillPreparationLinkRepository extends JpaRepository<PersonalSkillPreparationLink, UUID> {
    Optional<PersonalSkillPreparationLink> findByBacklogIdAndPrepItemId(UUID backlogId, UUID prepItemId);
    List<PersonalSkillPreparationLink> findByBacklogId(UUID backlogId);
}

interface SkillLearningResourceRepository extends JpaRepository<SkillLearningResource, UUID> {
    List<SkillLearningResource> findByBacklogIdOrderByCreatedAtAsc(UUID backlogId);
}

interface SkillProjectEvidenceRepository extends JpaRepository<SkillProjectEvidence, UUID> {
    List<SkillProjectEvidence> findByBacklogIdOrderByCreatedAtAsc(UUID backlogId);
}
