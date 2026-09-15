package dev.dhruv.jobsearch.profile;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalUserProfileRepository extends JpaRepository<LocalUserProfile, UUID> {
}
