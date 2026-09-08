package dev.dhruv.jobsearch.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.imports.daily-high-fit.enabled", havingValue = "true")
public class DailyHighFitStartupImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyHighFitStartupImporter.class);
    private final DailyHighFitImportService importService;

    public DailyHighFitStartupImporter(DailyHighFitImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var report = importService.importConfiguredFolder();
            LOGGER.info("Daily high-fit import complete: {} imported, {} unchanged, {} failed",
                    report.filesImported(), report.filesUnchanged(), report.filesFailed());
        } catch (Exception exception) {
            LOGGER.warn("Daily high-fit startup import could not run: {}", exception.getMessage());
        }
    }
}
