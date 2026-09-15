package dev.dhruv.jobsearch.assistance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistance_run")
public class AssistanceRun {

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AssistanceUseCase useCase;
    @Column(nullable = false) private UUID targetId;
    @Column(nullable = false, length = 64) private String inputHash;
    @Column(nullable = false, length = 80) private String provider;
    @Column(nullable = false, length = 160) private String model;
    @Column(nullable = false, length = 80) private String promptVersion;
    @Column(nullable = false, length = 80) private String schemaVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AssistanceRunStatus status;
    @Column(columnDefinition = "text") private String resultPayload;
    @Column(length = 240) private String providerResponseId;
    private Integer inputTokens;
    private Integer outputTokens;
    @Column(columnDefinition = "text") private String errorMessage;
    @Column(nullable = false) private Instant createdAt;
    private Instant completedAt;

    protected AssistanceRun() {}

    public AssistanceRun(AssistanceUseCase useCase, UUID targetId, String inputHash, String provider, String model,
            String promptVersion, String schemaVersion) {
        this.id = UUID.randomUUID();
        this.useCase = useCase;
        this.targetId = targetId;
        this.inputHash = inputHash;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.status = AssistanceRunStatus.RUNNING;
        this.createdAt = Instant.now();
    }

    public void complete(String resultPayload, String providerResponseId, Integer inputTokens, Integer outputTokens) {
        if (status != AssistanceRunStatus.RUNNING) throw new IllegalStateException("This assistance run is already final.");
        this.resultPayload = resultPayload;
        this.providerResponseId = providerResponseId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = AssistanceRunStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        if (status != AssistanceRunStatus.RUNNING) return;
        this.errorMessage = errorMessage;
        this.status = AssistanceRunStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AssistanceUseCase getUseCase() { return useCase; }
    public UUID getTargetId() { return targetId; }
    public String getInputHash() { return inputHash; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public AssistanceRunStatus getStatus() { return status; }
    public String getResultPayload() { return resultPayload; }
    public String getProviderResponseId() { return providerResponseId; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
