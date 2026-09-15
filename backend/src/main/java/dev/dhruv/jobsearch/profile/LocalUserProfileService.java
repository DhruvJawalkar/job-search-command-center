package dev.dhruv.jobsearch.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalUserProfileService {

    private final LocalUserProfileRepository repository;

    public LocalUserProfileService(LocalUserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LocalUserProfile get() {
        return repository.findById(LocalUserProfile.SINGLE_USER_ID).orElseGet(LocalUserProfile::new);
    }

    @Transactional
    public LocalUserProfile save(LocalUserProfile.ProfileValues values) {
        LocalUserProfile profile = repository.findById(LocalUserProfile.SINGLE_USER_ID)
                .orElseGet(LocalUserProfile::new);
        profile.update(values);
        return repository.save(profile);
    }
}
