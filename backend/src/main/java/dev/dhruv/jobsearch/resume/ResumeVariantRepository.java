package dev.dhruv.jobsearch.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVariantRepository extends JpaRepository<ResumeVariant, UUID> {

    List<ResumeVariant> findByActiveTrueOrderByUpdatedAtDesc();

    Optional<ResumeVariant> findByNameIgnoreCase(String name);
}
