package dev.dhruv.jobsearch.privacy;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_lifecycle_operation")
public class DataLifecycleOperation {

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private OperationType operationType;
    @Column(nullable = false, length = 500) private String categoryScope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private OperationStatus status;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String planDigest;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String confirmationTokenHash;
    @Column(length = 160) private String confirmationPhrase;
    @Column(nullable = false) private long plannedDatabaseRows;
    @Column(nullable = false) private long plannedFileCount;
    @Column(nullable = false) private long plannedByteCount;
    @Column(nullable = false) private long skippedUnsafeEntryCount;
    private Long affectedDatabaseRows;
    private Long deletedFileCount;
    private Long failedFileCount;
    private Long exportByteCount;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant completedAt;

    protected DataLifecycleOperation() {}

    DataLifecycleOperation(OperationType operationType, Set<DataLifecycleInventoryService.Category> categories,
            String planDigest, String confirmationTokenHash, String confirmationPhrase,
            long databaseRows, long fileCount, long byteCount, long skippedUnsafeEntries,
            Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.operationType = operationType;
        this.categoryScope = categories.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.status = OperationStatus.PREVIEWED;
        this.planDigest = planDigest;
        this.confirmationTokenHash = confirmationTokenHash;
        this.confirmationPhrase = confirmationPhrase;
        this.plannedDatabaseRows = databaseRows;
        this.plannedFileCount = fileCount;
        this.plannedByteCount = byteCount;
        this.skippedUnsafeEntryCount = skippedUnsafeEntries;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    Set<DataLifecycleInventoryService.Category> categories() {
        if (categoryScope.isBlank()) return EnumSet.noneOf(DataLifecycleInventoryService.Category.class);
        return Arrays.stream(categoryScope.split(","))
                .map(DataLifecycleInventoryService.Category::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DataLifecycleInventoryService.Category.class)));
    }

    void markStale(Instant now) { status = OperationStatus.STALE; completedAt = now; }
    void markExpired(Instant now) { status = OperationStatus.EXPIRED; completedAt = now; }
    void markDatabaseDeleted(long affectedRows) {
        status = OperationStatus.DATABASE_DELETED;
        affectedDatabaseRows = affectedRows;
    }
    void markDeleteFinished(long deletedFiles, long failedFiles, Instant now) {
        deletedFileCount = (deletedFileCount == null ? 0 : deletedFileCount) + deletedFiles;
        failedFileCount = failedFiles;
        status = failedFiles == 0 ? OperationStatus.COMPLETED : OperationStatus.PARTIAL_FILESYSTEM_FAILURE;
        completedAt = failedFiles == 0 ? now : null;
    }
    void markExported(long bytes, Instant now) {
        exportByteCount = bytes;
        status = OperationStatus.EXPORT_COMPLETED;
        completedAt = now;
    }
    void markFailed(Instant now) { status = OperationStatus.FAILED; completedAt = now; }

    public UUID getId() { return id; }
    public OperationType getOperationType() { return operationType; }
    public String getCategoryScope() { return categoryScope; }
    public OperationStatus getStatus() { return status; }
    public String getPlanDigest() { return planDigest; }
    public String getConfirmationTokenHash() { return confirmationTokenHash; }
    public String getConfirmationPhrase() { return confirmationPhrase; }
    public long getPlannedDatabaseRows() { return plannedDatabaseRows; }
    public long getPlannedFileCount() { return plannedFileCount; }
    public long getPlannedByteCount() { return plannedByteCount; }
    public long getSkippedUnsafeEntryCount() { return skippedUnsafeEntryCount; }
    public Long getAffectedDatabaseRows() { return affectedDatabaseRows; }
    public Long getDeletedFileCount() { return deletedFileCount; }
    public Long getFailedFileCount() { return failedFileCount; }
    public Long getExportByteCount() { return exportByteCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }

    public enum OperationType { EXPORT, DELETE_CATEGORIES, DELETE_ALL }
    public enum OperationStatus {
        PREVIEWED, EXPORT_COMPLETED, DATABASE_DELETED, COMPLETED,
        PARTIAL_FILESYSTEM_FAILURE, FAILED, STALE, EXPIRED
    }
}
