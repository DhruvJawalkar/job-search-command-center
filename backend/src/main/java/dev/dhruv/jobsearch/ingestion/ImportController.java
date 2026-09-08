package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final DailyHighFitImportService importService;
    private final ImportBatchRepository batchRepository;

    public ImportController(DailyHighFitImportService importService, ImportBatchRepository batchRepository) {
        this.importService = importService;
        this.batchRepository = batchRepository;
    }

    @PostMapping("/daily-high-fit")
    DailyHighFitImportService.ImportRunReport importDailyHighFit() {
        return importService.importConfiguredFolder();
    }

    @GetMapping
    List<ImportBatchResponse> batches() {
        return batchRepository.findTop20ByOrderByStartedAtDesc().stream().map(ImportBatchResponse::from).toList();
    }

    public record ImportBatchResponse(
            UUID id,
            String sourceFile,
            String actionSourceFile,
            LocalDate sourceDate,
            ImportStatus status,
            int rowsSeen,
            int opportunitiesCreated,
            int opportunitiesUpdated,
            int observationsCreated,
            int observationsUpdated,
            int actionsCreated,
            int actionsUpdated,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {

        static ImportBatchResponse from(ImportBatch batch) {
            return new ImportBatchResponse(batch.getId(), batch.getSourceFile(), batch.getActionSourceFile(),
                    batch.getSourceDate(), batch.getStatus(), batch.getRowsSeen(), batch.getOpportunitiesCreated(),
                    batch.getOpportunitiesUpdated(), batch.getObservationsCreated(), batch.getObservationsUpdated(),
                    batch.getActionsCreated(), batch.getActionsUpdated(), batch.getErrorMessage(),
                    batch.getStartedAt(), batch.getCompletedAt());
        }
    }
}
