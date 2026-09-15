package dev.dhruv.jobsearch.summary;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LocalSummaryPreferencesRepository extends JpaRepository<LocalSummaryPreferences, UUID> {}
