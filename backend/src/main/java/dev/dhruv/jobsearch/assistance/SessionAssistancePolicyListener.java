package dev.dhruv.jobsearch.assistance;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;

/** Clears non-durable context only after a policy transition commits. */
@Component
class SessionAssistancePolicyListener {

    private final SessionAssistanceStore sessionStore;

    SessionAssistancePolicyListener(SessionAssistanceStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void policyModeChanged(PrivacyPolicyService.AssistanceContextModeChanged event) {
        sessionStore.clear();
    }
}
