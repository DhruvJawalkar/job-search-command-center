package dev.dhruv.jobsearch.application;

public enum ApplicationStage {
    DRAFT,
    APPLIED,
    RECRUITER_SCREEN,
    INTERVIEWING,
    OFFER,
    ACCEPTED,
    WITHDRAWN,
    REJECTED,
    GHOSTED,
    CLOSED;

    public boolean isTerminal() {
        return this == ACCEPTED || this == WITHDRAWN || this == REJECTED || this == GHOSTED || this == CLOSED;
    }
}

