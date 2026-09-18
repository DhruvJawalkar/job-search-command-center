package dev.dhruv.jobsearch.connected;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transmission_receipt")
class TransmissionReceipt {
    @Id private UUID id;
    @Column(nullable = false) private UUID previewId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private TransmissionOperation operation;
    @Column(nullable = false, length = 500) private String destination;
    @Column(nullable = false, length = 240) private String purpose;
    @Column(nullable = false, length = 1000) private String minimizedFields;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransmissionOutcome outcome;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant completedAt;

    protected TransmissionReceipt() {}

    TransmissionReceipt(TransmissionPreview preview, TransmissionOutcome outcome, Instant completedAt) {
        this.id = UUID.randomUUID();
        this.previewId = preview.getId();
        this.operation = preview.getOperation();
        this.destination = preview.getDestination();
        this.purpose = preview.getPurpose();
        this.minimizedFields = preview.getMinimizedFields();
        this.payloadHash = preview.getPayloadHash();
        this.outcome = outcome;
        this.createdAt = preview.getCreatedAt();
        this.completedAt = completedAt;
    }

    UUID getId() { return id; }
    UUID getPreviewId() { return previewId; }
    TransmissionOperation getOperation() { return operation; }
    String getDestination() { return destination; }
    String getPurpose() { return purpose; }
    String getMinimizedFields() { return minimizedFields; }
    String getPayloadHash() { return payloadHash; }
    TransmissionOutcome getOutcome() { return outcome; }
    Instant getCreatedAt() { return createdAt; }
    Instant getCompletedAt() { return completedAt; }
}
