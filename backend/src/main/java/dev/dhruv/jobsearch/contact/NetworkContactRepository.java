package dev.dhruv.jobsearch.contact;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkContactRepository extends JpaRepository<NetworkContact, UUID> {
    List<NetworkContact> findAllByOrderByFullNameAsc();
}
