package dev.dhruv.jobsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/installation")
public class InstallationStatusController {

    private final boolean demoMode;

    public InstallationStatusController(@Value("${app.demo-mode:false}") boolean demoMode) {
        this.demoMode = demoMode;
    }

    @GetMapping
    InstallationStatus status() {
        return new InstallationStatus(demoMode ? "DEMO" : "PERSONAL", demoMode, "1.0.0");
    }

    public record InstallationStatus(String mode, boolean demoMode, String version) {}
}
