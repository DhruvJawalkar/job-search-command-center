package dev.dhruv.jobsearch.ingestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

public interface OpportunityObservationRepository extends JpaRepository<OpportunityObservation, UUID> {

    Optional<OpportunityObservation> findByOpportunityIdAndObservedOn(UUID opportunityId, LocalDate observedOn);

    List<OpportunityObservation> findByOpportunityIdOrderByObservedOnDesc(UUID opportunityId);

    @EntityGraph(attributePaths = "opportunity")
    List<OpportunityObservation> findByObservedOnOrderBySourceRankAsc(LocalDate observedOn);

    @EntityGraph(attributePaths = "opportunity")
    List<OpportunityObservation> findAllByOrderByObservedOnDescSourceRankAsc();

    @Query("select distinct o.observedOn from OpportunityObservation o order by o.observedOn desc")
    List<LocalDate> findObservationDates();

    @Query("select max(o.observedOn) from OpportunityObservation o")
    LocalDate findLatestObservationDate();
}
