package dev.dhruv.jobsearch.privacy;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/privacy-policy")
public class PrivacyPolicyController {

    private final PrivacyPolicyService service;
    private final DataLifecycleInventoryService inventory;
    private final DataLifecycleService lifecycle;

    public PrivacyPolicyController(PrivacyPolicyService service, DataLifecycleInventoryService inventory,
            DataLifecycleService lifecycle) {
        this.service = service;
        this.inventory = inventory;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    PrivacyPolicyService.PolicyView get() { return service.get(); }

    @PutMapping
    PrivacyPolicyService.PolicyView update(@Valid @RequestBody UpdatePolicyRequest request) {
        return service.update(new PrivacyPolicyService.UpdatePolicy(request.assistanceContextMode(),
                request.derivedContextRetentionDays(), request.transientIngestionRetentionDays(),
                request.connectedAssistanceEnabled(),
                request.consentTextVersion(), request.consentAccepted()));
    }

    @GetMapping("/cleanup-preview")
    PrivacyPolicyService.CleanupPreview cleanupPreview() { return service.previewCleanup(); }

    @PostMapping("/cleanup-preview")
    PrivacyPolicyService.CleanupPreview proposedCleanupPreview(
            @Valid @RequestBody ProposedCleanupPreviewRequest request) {
        return service.previewProposedCleanup(request.assistanceContextMode(), request.derivedContextRetentionDays(),
                request.transientIngestionRetentionDays());
    }

    @PostMapping("/cleanup")
    PrivacyPolicyService.CleanupReceiptView cleanup(
            @RequestHeader("X-JSCC-Confirmation") String confirmation) {
        if (!"run-derived-cleanup".equals(confirmation)) {
            throw new IllegalArgumentException("Confirm this derived-context cleanup from the local application.");
        }
        return service.cleanup();
    }

    @GetMapping("/data-inventory")
    DataLifecycleInventoryService.InventoryView dataInventory() { return inventory.inventory(); }

    @PostMapping("/data-lifecycle/preview")
    DataLifecycleService.PreviewView dataLifecyclePreview(@RequestHeader("X-JSCC-Action") String action,
            @Valid @RequestBody DataLifecyclePreviewRequest request) {
        requireAction(action, DataLifecycleService.PREVIEW_HEADER);
        return lifecycle.preview(new DataLifecycleService.PreviewCommand(request.operationType(), request.categories()));
    }

    @PostMapping("/data-lifecycle/export")
    ResponseEntity<byte[]> exportData(@RequestHeader("X-JSCC-Action") String action,
            @Valid @RequestBody DataLifecycleExecuteRequest request) {
        requireAction(action, DataLifecycleService.EXPORT_HEADER);
        DataLifecycleService.ExportResult result = lifecycle.export(request.operationId(), request.previewToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header("X-JSCC-Operation-Id", result.receipt().operationId().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(result.archive());
    }

    @PostMapping("/data-lifecycle/delete")
    DataLifecycleService.OperationReceiptView deleteData(@RequestHeader("X-JSCC-Action") String action,
            @Valid @RequestBody DataLifecycleExecuteRequest request) {
        requireAction(action, DataLifecycleService.DELETE_HEADER);
        return lifecycle.delete(request.operationId(), request.previewToken(), request.confirmationPhrase());
    }

    @GetMapping("/data-lifecycle/operations/{id}")
    DataLifecycleService.OperationReceiptView dataLifecycleReceipt(@PathVariable UUID id) {
        return lifecycle.receipt(id);
    }

    private void requireAction(String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Confirm this action from the local application.");
    }

    public record UpdatePolicyRequest(AssistanceContextMode assistanceContextMode,
            Integer derivedContextRetentionDays, Integer transientIngestionRetentionDays,
            boolean connectedAssistanceEnabled,
            @Size(max = 80) String consentTextVersion, boolean consentAccepted) {
        public UpdatePolicyRequest(AssistanceContextMode assistanceContextMode, Integer derivedContextRetentionDays,
                boolean connectedAssistanceEnabled, String consentTextVersion, boolean consentAccepted) {
            this(assistanceContextMode, derivedContextRetentionDays, null, connectedAssistanceEnabled,
                    consentTextVersion, consentAccepted);
        }
    }

    public record ProposedCleanupPreviewRequest(AssistanceContextMode assistanceContextMode,
            Integer derivedContextRetentionDays, Integer transientIngestionRetentionDays) {
        public ProposedCleanupPreviewRequest(AssistanceContextMode assistanceContextMode,
                Integer derivedContextRetentionDays) {
            this(assistanceContextMode, derivedContextRetentionDays, null);
        }
    }

    public record DataLifecyclePreviewRequest(@NotNull DataLifecycleOperation.OperationType operationType,
            Set<DataLifecycleInventoryService.Category> categories) {}

    public record DataLifecycleExecuteRequest(@NotNull UUID operationId,
            @NotNull @Size(min = 20, max = 200) String previewToken,
            @Size(max = 160) String confirmationPhrase) {}
}
