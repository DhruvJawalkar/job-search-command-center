package dev.dhruv.jobsearch.connected;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface TransmissionPreviewRepository extends JpaRepository<TransmissionPreview, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransmissionPreview> findByTokenHash(String tokenHash);
}

interface TransmissionReceiptRepository extends JpaRepository<TransmissionReceipt, UUID> {
    List<TransmissionReceipt> findTop25ByOrderByCompletedAtDesc();
}
