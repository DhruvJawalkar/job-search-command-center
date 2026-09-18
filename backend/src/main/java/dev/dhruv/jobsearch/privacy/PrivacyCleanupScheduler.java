package dev.dhruv.jobsearch.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class PrivacyCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacyCleanupScheduler.class);

    private final PrivacyPolicyService service;

    public PrivacyCleanupScheduler(PrivacyPolicyService service) { this.service = service; }

    @EventListener(ApplicationReadyEvent.class)
    public void runStartupCleanup() { runSafely(true); }

    @Scheduled(fixedDelayString = "${app.privacy.cleanup.poll-delay:PT1H}")
    public void runDueDailyCleanup() { runSafely(false); }

    private void runSafely(boolean startup) {
        try {
            service.automaticCleanupIfEligible(startup);
        } catch (RuntimeException exception) {
            // Do not log policy contents, payloads, paths, or provider errors from cleanup candidates.
            LOGGER.error("Privacy cleanup failed during {} enforcement (exception type: {}).",
                    startup ? "startup" : "scheduled", exception.getClass().getSimpleName());
        }
    }
}
