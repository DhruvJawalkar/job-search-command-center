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
@Table(name = "transmission_preview")
class TransmissionPreview {
    @Id private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 64, columnDefinition = "char(64)") private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private TransmissionOperation operation;
    @Column(nullable = false, length = 500) private String destination;
    @Column(nullable = false, length = 240) private String purpose;
    @Column(nullable = false, length = 1000) private String minimizedFields;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String payloadHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    @Column(nullable = false) private Instant createdAt;

    protected TransmissionPreview() {}

    TransmissionPreview(String tokenHash, TransmissionOperation operation, String destination, String purpose,
            String minimizedFields, String payloadHash, Instant expiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.operation = operation;
        this.destination = destination;
        this.purpose = purpose;
        this.minimizedFields = minimizedFields;
        this.payloadHash = payloadHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    void consume(Instant now) {
        if (consumedAt != null) throw new IllegalStateException("This transmission confirmation has already been used.");
        if (!expiresAt.isAfter(now)) throw new IllegalStateException("This transmission confirmation has expired. Preview the request again.");
        consumedAt = now;
    }

    UUID getId() { return id; }
    TransmissionOperation getOperation() { return operation; }
    String getDestination() { return destination; }
    String getPurpose() { return purpose; }
    String getMinimizedFields() { return minimizedFields; }
    String getPayloadHash() { return payloadHash; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getCreatedAt() { return createdAt; }
}
