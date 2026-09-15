package dev.dhruv.jobsearch.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DailyHighFitImportService {

    private final Path configuredFolder;
    private final ImportBatchRepository batchRepository;
    private final DailyHighFitFileImporter fileImporter;

    public DailyHighFitImportService(
            @Value("${app.imports.daily-high-fit.folder:../daily-high-fit-job-roles}") String configuredFolder,
            ImportBatchRepository batchRepository,
            DailyHighFitFileImporter fileImporter) {
        this.configuredFolder = Path.of(configuredFolder).toAbsolutePath().normalize();
        this.batchRepository = batchRepository;
        this.fileImporter = fileImporter;
    }

    public ImportRunReport importConfiguredFolder() {
        if (!Files.isDirectory(configuredFolder)) {
            throw new IllegalStateException("Daily high-fit import folder was not found: " + configuredFolder);
        }

        List<Path> workbooks;
        try (var files = Files.list(configuredFolder)) {
            workbooks = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}-high-fit-openings\\.xlsx"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list daily high-fit files.", exception);
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int opportunitiesCreated = 0;
        int observationsCreated = 0;
        int actionsCreated = 0;
        List<FileImportResult> results = new ArrayList<>();

        for (Path workbook : workbooks) {
            String fileName = workbook.getFileName().toString();
            LocalDate sourceDate = LocalDate.parse(fileName.substring(0, 10));
            Path actionCandidate = configuredFolder.resolve(sourceDate + "-top-three-actions.txt");
            Path actionPath = Files.isRegularFile(actionCandidate) ? actionCandidate : null;
            String contentHash;
            try {
                contentHash = combinedHash(workbook, actionPath);
            } catch (IOException exception) {
                failed++;
                results.add(new FileImportResult(fileName, sourceDate, "FAILED", exception.getMessage(), null));
                continue;
            }

            var previous = batchRepository.findByContentHash(contentHash);
            if (previous.isPresent()) {
                var batch = previous.get();
                if (batch.getStatus() != ImportStatus.COMPLETED) {
                    failed++;
                    results.add(new FileImportResult(fileName, sourceDate, "FAILED",
                            batch.getErrorMessage() == null ? "Previous import did not complete. Check and correct the workbook before retrying." : batch.getErrorMessage(), batch.getId()));
                } else {
                    skipped++;
                    results.add(new FileImportResult(fileName, sourceDate, "UNCHANGED", null, batch.getId()));
                }
                continue;
            }

            try {
                ImportBatch batch = fileImporter.importFile(workbook, actionPath, sourceDate, contentHash);
                imported++;
                opportunitiesCreated += batch.getOpportunitiesCreated();
                observationsCreated += batch.getObservationsCreated();
                actionsCreated += batch.getActionsCreated();
                results.add(new FileImportResult(fileName, sourceDate, "IMPORTED", null, batch.getId()));
            } catch (Exception exception) {
                failed++;
                String message = rootMessage(exception);
                ImportBatch failure = fileImporter.recordFailure(workbook, actionPath, sourceDate, contentHash, message);
                results.add(new FileImportResult(fileName, sourceDate, "FAILED", message, failure.getId()));
            }
        }

        return new ImportRunReport(configuredFolder.toString(), workbooks.size(), imported, skipped, failed,
                opportunitiesCreated, observationsCreated, actionsCreated, results);
    }

    public Path configuredFolder() {
        return configuredFolder;
    }

    private String combinedHash(Path workbook, Path actionPath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DailyHighFitFileImporter.IMPORTER_VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(workbook));
            digest.update((byte) 0);
            if (actionPath != null) digest.update(Files.readAllBytes(actionPath));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record ImportRunReport(
            String folder,
            int filesFound,
            int filesImported,
            int filesUnchanged,
            int filesFailed,
            int opportunitiesCreated,
            int observationsCreated,
            int actionsCreated,
            List<FileImportResult> files) {
    }

    public record FileImportResult(String file, LocalDate sourceDate, String result, String error, java.util.UUID batchId) {
    }
}
