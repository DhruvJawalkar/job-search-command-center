package dev.dhruv.jobsearch.privacy;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Short> {}

interface PrivacyCleanupReceiptRepository extends JpaRepository<PrivacyCleanupReceipt, UUID> {}

interface DataLifecycleOperationRepository extends JpaRepository<DataLifecycleOperation, UUID> {}

interface PrivacyAssistanceCleanupRepository extends JpaRepository<PrivacyCleanupReceipt, UUID> {

    @Query("select count(r) from AssistanceRun r where r.createdAt < :cutoff")
    long countRunsBefore(@Param("cutoff") Instant cutoff);

    @Query("select count(d) from AssistanceDecision d where d.run.createdAt < :cutoff")
    long countDecisionsForRunsBefore(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistanceDecision d where d.run.id in (select r.id from AssistanceRun r where r.createdAt < :cutoff)")
    int deleteDecisionsForRunsBefore(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssistanceRun r where r.createdAt < :cutoff")
    int deleteRunsBefore(@Param("cutoff") Instant cutoff);
}
