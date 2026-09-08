package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.ingestion.DailyHighFitImportService;
import dev.dhruv.jobsearch.ingestion.DailyHighFitFileImporter;
import dev.dhruv.jobsearch.ingestion.DailyPriorityActionRepository;
import dev.dhruv.jobsearch.ingestion.ImportBatchRepository;
import dev.dhruv.jobsearch.ingestion.OpportunityObservationRepository;

@SpringBootTest
@Transactional
class DailyHighFitImportFlowTest {

    @Autowired
    private DailyHighFitFileImporter fileImporter;

    @TempDir
    Path importFolder;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Autowired
    private OpportunityObservationRepository observationRepository;

    @Autowired
    private DailyPriorityActionRepository actionRepository;

    @Test
    void importsTheWeeklyFilesAndSkipsIdenticalContentOnReplay() throws Exception {
        // Never depend on the user's private, changing daily-opening workbooks.
        for (int day = 25; day <= 28; day++) {
            writeWorkbook(LocalDate.of(2026, 8, day));
        }
        // Both supported numbering styles; other dates deliberately have no companion.
        Files.writeString(importFolder.resolve("2026-08-28-top-three-actions.txt"),
                "1. Apply to Fixture Alpha\n2) Apply to Fixture Beta\n3. Apply to Fixture Gamma\n");
        Files.writeString(importFolder.resolve("unrelated.txt"), "Not an import source");
        var importService = new DailyHighFitImportService(importFolder.toString(), batchRepository, fileImporter);
        var firstRun = importService.importConfiguredFolder();

        assertThat(firstRun.filesFound()).isEqualTo(4);
        assertThat(firstRun.filesImported()).isEqualTo(firstRun.filesFound());
        assertThat(firstRun.filesFailed()).isZero();
        assertThat(firstRun.opportunitiesCreated()).isEqualTo(3);
        assertThat(firstRun.observationsCreated()).isEqualTo(12);
        // Action companions are optional; imported workbooks without one still remain valid.
        assertThat(firstRun.actionsCreated()).isEqualTo(3);
        assertThat(batchRepository.count()).isEqualTo(firstRun.filesFound());
        assertThat(observationRepository.count()).isEqualTo(firstRun.observationsCreated());
        assertThat(actionRepository.findByActionDateOrderByPriorityRankAsc(actionRepository.findLatestActionDate()))
                .hasSize(3)
                .allSatisfy(action -> assertThat(action.getOpportunity()).isNotNull());
        assertThat(actionRepository.findLatestActionDate()).isEqualTo(LocalDate.of(2026, 8, 28));

        var replay = importService.importConfiguredFolder();

        assertThat(replay.filesImported()).isZero();
        assertThat(replay.filesUnchanged()).isEqualTo(firstRun.filesFound());
        assertThat(replay.filesFailed()).isZero();
        assertThat(batchRepository.count()).isEqualTo(firstRun.filesFound());
        assertThat(observationRepository.count()).isEqualTo(firstRun.observationsCreated());
        assertThat(actionRepository.count()).isEqualTo(3);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void replayOfAnUnfixedFailureStaysFailed() throws Exception {
        var date = LocalDate.of(2026, 8, 28);
        Files.writeString(importFolder.resolve(date+"-high-fit-openings.xlsx"), "Invalid synthetic workbook");
        var service = new DailyHighFitImportService(importFolder.toString(), batchRepository, fileImporter);
        var first = service.importConfiguredFolder();
        var replay = service.importConfiguredFolder();
        assertThat(first.filesFailed()).isEqualTo(1);
        assertThat(replay.filesFailed()).isEqualTo(1);
        assertThat(replay.filesUnchanged()).isZero();
        assertThat(replay.files().getFirst().error()).isNotBlank();
        assertThat(replay.files().getFirst().batchId()).isEqualTo(first.files().getFirst().batchId());
        var failed = batchRepository.findById(first.files().getFirst().batchId()).orElseThrow();
        var hash = failed.getContentHash();
        // Emulate an incomplete batch left by an older importer or interrupted transaction.
        batchRepository.deleteById(first.files().getFirst().batchId());
        var incomplete = batchRepository.saveAndFlush(new dev.dhruv.jobsearch.ingestion.ImportBatch(
                date+"-high-fit-openings.xlsx",null,date,hash));
        assertThat(service.importConfiguredFolder().filesFailed()).isEqualTo(1);
        // This test uses real transaction boundaries: clean only its committed fixture.
        batchRepository.deleteById(incomplete.getId());
    }

    private void writeWorkbook(LocalDate date) throws Exception {
        var headers = List.of("Rank", "Company", "Exact Title", "Location / Work Arrangement", "Posting Date",
                "Overall Fit", "Recruiter-Screen Strength", "Technical Scope", "Growth Potential",
                "Weighted Total", "Recommendation", "Role Summary", "Fit Rationale", "Key Risks / Gaps",
                "Direct Job Link", "Recommended Resume Variant", "Authorization / Eligibility", "Verified Date");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("High-Fit Openings");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
            }
            var companies = List.of("Fixture Alpha", "Fixture Beta", "Fixture Gamma");
            for (int index = 0; index < companies.size(); index++) {
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(index + 1);
                row.createCell(1).setCellValue(companies.get(index));
                row.createCell(2).setCellValue("Senior Backend Engineer");
                row.createCell(3).setCellValue("Bengaluru / Hybrid");
                for (int column = 5; column <= 9; column++) row.createCell(column).setCellValue(9);
                row.createCell(10).setCellValue("Apply");
                row.createCell(11).setCellValue("Synthetic role observed on " + date);
                row.createCell(12).setCellValue("Synthetic backend fit evidence");
                row.createCell(14).setCellValue("https://example.invalid/jobs/fixture-" + index);
            }
            try (var output = Files.newOutputStream(importFolder.resolve(date + "-high-fit-openings.xlsx"))) {
                workbook.write(output);
            }
        }
    }
}
