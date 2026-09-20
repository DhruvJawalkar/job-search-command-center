package dev.dhruv.jobsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/installation")
public class InstallationStatusController {

    private final boolean demoMode;
    private final String version;
    private final String distributionChannel;
    private final boolean codexGuidanceEnabled;

    public InstallationStatusController(
            @Value("${app.demo-mode:false}") boolean demoMode,
            @Value("${app.version:1.0.0}") String version,
            @Value("${app.distribution-channel:source}") String distributionChannel,
            @Value("${app.codex-guidance-enabled:true}") boolean codexGuidanceEnabled) {
        this.demoMode = demoMode;
        this.version = version;
        this.distributionChannel = distributionChannel;
        this.codexGuidanceEnabled = codexGuidanceEnabled;
    }

    @GetMapping
    InstallationStatus status() {
        return new InstallationStatus(demoMode ? "DEMO" : "PERSONAL", demoMode, version,
                distributionChannel, codexGuidanceEnabled);
    }

    public record InstallationStatus(String mode, boolean demoMode, String version,
            String distributionChannel, boolean codexGuidanceEnabled) {}
}
