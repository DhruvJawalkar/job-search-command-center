package dev.dhruv.jobsearch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class InstallationStatusControllerTest {

    @Test
    void reportsSourceDistributionWithCodexGuidanceUsingCurrentDefaults() {
        var status = loadStatus();

        assertThat(status.mode()).isEqualTo("PERSONAL");
        assertThat(status.demoMode()).isFalse();
        assertThat(status.version()).isEqualTo("1.0.0");
        assertThat(status.distributionChannel()).isEqualTo("source");
        assertThat(status.codexGuidanceEnabled()).isTrue();
    }

    @Test
    void reportsStandaloneDistributionWithoutCodexGuidance() {
        var status = loadStatus(
                "app.demo-mode=true",
                "app.version=1.1.0",
                "app.distribution-channel=standalone",
                "app.codex-guidance-enabled=false");

        assertThat(status.mode()).isEqualTo("DEMO");
        assertThat(status.demoMode()).isTrue();
        assertThat(status.version()).isEqualTo("1.1.0");
        assertThat(status.distributionChannel()).isEqualTo("standalone");
        assertThat(status.codexGuidanceEnabled()).isFalse();
    }

    private InstallationStatusController.InstallationStatus loadStatus(String... properties) {
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(properties).applyTo(context);
            context.register(InstallationStatusController.class);
            context.refresh();
            return context.getBean(InstallationStatusController.class).status();
        }
    }
}
