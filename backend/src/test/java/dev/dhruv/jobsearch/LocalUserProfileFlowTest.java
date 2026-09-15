package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.profile.LocalUserProfile;
import dev.dhruv.jobsearch.profile.LocalUserProfileService;

@SpringBootTest
@Transactional
class LocalUserProfileFlowTest {

    @Autowired LocalUserProfileService service;

    @Test
    void storesSingleUserTargetingPreferencesLocally() {
        assertThat(service.get().isOnboardingCompleted()).isFalse();

        var saved = service.save(new LocalUserProfile.ProfileValues(
                "Alex Morgan", "Staff Backend Engineer\nPlatform Engineer", "Staff",
                "Bengaluru\nRemote", "Hybrid, Remote", "Product engineering, developer tools", "200-5000",
                "Example Systems", "Grow into organization-wide technical leadership",
                "Ownership, learning, respectful challenge", "Java, distributed systems", "Ad-tech",
                "08:30", "Asia/Kolkata", true));

        assertThat(saved.getId()).isEqualTo(LocalUserProfile.SINGLE_USER_ID);
        assertThat(service.get().getTargetRoles()).contains("Staff Backend Engineer");
        assertThat(service.get().isOnboardingCompleted()).isTrue();
    }
}
