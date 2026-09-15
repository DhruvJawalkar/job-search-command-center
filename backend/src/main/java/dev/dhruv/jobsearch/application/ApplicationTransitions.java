package dev.dhruv.jobsearch.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class ApplicationTransitions {

    private static final Map<ApplicationStage, Set<ApplicationStage>> ALLOWED = allowedTransitions();

    private ApplicationTransitions() {
    }

    static boolean canTransition(ApplicationStage from, ApplicationStage to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    private static Map<ApplicationStage, Set<ApplicationStage>> allowedTransitions() {
        Map<ApplicationStage, Set<ApplicationStage>> transitions = new EnumMap<>(ApplicationStage.class);
        transitions.put(ApplicationStage.DRAFT,
                EnumSet.of(ApplicationStage.APPLIED, ApplicationStage.WITHDRAWN));
        transitions.put(ApplicationStage.APPLIED,
                EnumSet.of(ApplicationStage.RECRUITER_SCREEN, ApplicationStage.REJECTED,
                        ApplicationStage.GHOSTED, ApplicationStage.WITHDRAWN, ApplicationStage.CLOSED));
        transitions.put(ApplicationStage.RECRUITER_SCREEN,
                EnumSet.of(ApplicationStage.INTERVIEWING, ApplicationStage.REJECTED,
                        ApplicationStage.WITHDRAWN, ApplicationStage.CLOSED));
        transitions.put(ApplicationStage.INTERVIEWING,
                EnumSet.of(ApplicationStage.OFFER, ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN));
        transitions.put(ApplicationStage.OFFER,
                EnumSet.of(ApplicationStage.ACCEPTED, ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN));
        return Map.copyOf(transitions);
    }
}

