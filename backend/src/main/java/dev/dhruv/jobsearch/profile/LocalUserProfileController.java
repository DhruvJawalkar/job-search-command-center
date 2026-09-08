package dev.dhruv.jobsearch.profile;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class LocalUserProfileController {

    private final LocalUserProfileService service;

    public LocalUserProfileController(LocalUserProfileService service) {
        this.service = service;
    }

    @GetMapping
    ProfileResponse get() { return ProfileResponse.from(service.get()); }

    @PutMapping
    ProfileResponse save(@Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(service.save(new LocalUserProfile.ProfileValues(
                request.displayName(), request.targetRoles(), request.targetLevel(), request.preferredLocations(),
                request.preferredWorkModes(), request.preferredCompanyTypes(), request.preferredCompanySizes(),
                request.previousEmployers(), request.careerGoals(), request.cultureValues(),
                request.includedTechnologies(), request.excludedTechnologies(), request.dailySearchTime(),
                request.timeZone(), request.onboardingCompleted())));
    }

    public record ProfileRequest(
            @Size(max = 120) String displayName,
            @Size(max = 2000) String targetRoles,
            @Size(max = 120) String targetLevel,
            @Size(max = 1500) String preferredLocations,
            @Size(max = 500) String preferredWorkModes,
            @Size(max = 1500) String preferredCompanyTypes,
            @Size(max = 500) String preferredCompanySizes,
            @Size(max = 2000) String previousEmployers,
            @Size(max = 3000) String careerGoals,
            @Size(max = 2000) String cultureValues,
            @Size(max = 2000) String includedTechnologies,
            @Size(max = 2000) String excludedTechnologies,
            @Size(max = 20) String dailySearchTime,
            @Size(max = 80) String timeZone,
            boolean onboardingCompleted) {}

    public record ProfileResponse(String displayName, String targetRoles, String targetLevel,
            String preferredLocations, String preferredWorkModes, String preferredCompanyTypes,
            String preferredCompanySizes, String previousEmployers, String careerGoals, String cultureValues,
            String includedTechnologies, String excludedTechnologies, String dailySearchTime, String timeZone,
            boolean onboardingCompleted, Instant updatedAt, long version) {
        static ProfileResponse from(LocalUserProfile profile) {
            return new ProfileResponse(profile.getDisplayName(), profile.getTargetRoles(), profile.getTargetLevel(),
                    profile.getPreferredLocations(), profile.getPreferredWorkModes(), profile.getPreferredCompanyTypes(),
                    profile.getPreferredCompanySizes(), profile.getPreviousEmployers(), profile.getCareerGoals(),
                    profile.getCultureValues(), profile.getIncludedTechnologies(), profile.getExcludedTechnologies(),
                    profile.getDailySearchTime(), profile.getTimeZone(), profile.isOnboardingCompleted(),
                    profile.getUpdatedAt(), profile.getVersion());
        }
    }
}
