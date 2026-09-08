package dev.dhruv.jobsearch.ingestion;

public enum GenericInboxItemStatus {
    RECEIVED,
    PARSED,
    NEEDS_REVIEW,
    PARTIALLY_REVIEWED,
    IMPORTED,
    REJECTED,
    FAILED
}
