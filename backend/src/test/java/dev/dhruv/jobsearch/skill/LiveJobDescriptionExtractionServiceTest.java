package dev.dhruv.jobsearch.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.dhruv.jobsearch.connected.TransmissionOperation;
import dev.dhruv.jobsearch.connected.TransmissionService;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.WorkMode;

class LiveJobDescriptionExtractionServiceTest {

    @Test
    void previewsTheExactPublicDestinationBeforeAnyFetch() {
        Fixture fixture = new Fixture();
        when(fixture.opportunities.findByDemoFalseOrderByDiscoveredAtDesc()).thenReturn(List.of(fixture.opening));
        var expected = new TransmissionService.PreviewView(java.util.UUID.randomUUID(), "one-time-token",
                TransmissionOperation.LIVE_JOB_PAGE_FETCH, "https://jobs.example.com", "purpose",
                List.of("sourceUrl"), Instant.now().plusSeconds(60));
        when(fixture.transmissions.issue(eq(TransmissionOperation.LIVE_JOB_PAGE_FETCH),
                eq("https://jobs.example.com"), any(), eq(List.of("sourceUrl")), any())).thenReturn(expected);

        var actual = fixture.service.preview(new LiveJobDescriptionExtractionService.LiveExtractionCommand(List.of()));

        assertThat(actual).isSameAs(expected);
        verifyNoInteractions(fixture.fetcher);
        verify(fixture.transmissions).issue(eq(TransmissionOperation.LIVE_JOB_PAGE_FETCH),
                eq("https://jobs.example.com"), any(), eq(List.of("sourceUrl")),
                eq(fixture.opening.getId() + "|https://jobs.example.com/roles/42"));
    }

    @Test
    void neverInvokesTheBrokerWhenTheOneTimeConfirmationIsRejected() {
        Fixture fixture = new Fixture();
        when(fixture.opportunities.findByDemoFalseOrderByDiscoveredAtDesc()).thenReturn(List.of(fixture.opening));
        when(fixture.transmissions.consume(eq("expired-token"), eq(TransmissionOperation.LIVE_JOB_PAGE_FETCH),
                eq("https://jobs.example.com"), any())).thenThrow(new IllegalArgumentException("invalid token"));

        assertThatThrownBy(() -> fixture.service.fetchAndExtract(
                new LiveJobDescriptionExtractionService.LiveExtractionCommand(List.of()), "expired-token"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid token");
        verifyNoInteractions(fixture.fetcher);
    }

    private static final class Fixture {
        final JobOpportunityRepository opportunities = mock(JobOpportunityRepository.class);
        final JobDescriptionSnapshotRepository snapshots = mock(JobDescriptionSnapshotRepository.class);
        final CanonicalSkillRepository skills = mock(CanonicalSkillRepository.class);
        final LiveJobPageFetcher fetcher = mock(LiveJobPageFetcher.class);
        final DeterministicSkillExtractionService extraction = mock(DeterministicSkillExtractionService.class);
        final TransmissionService transmissions = mock(TransmissionService.class);
        final JobOpportunity opening = new JobOpportunity("Example", "Staff Engineer", "Remote", WorkMode.REMOTE,
                "Example careers", "https://jobs.example.com/roles/42", "https://jobs.example.com/roles/42",
                null, Instant.parse("2026-09-18T00:00:00Z"));
        final LiveJobDescriptionExtractionService service = new LiveJobDescriptionExtractionService(opportunities,
                snapshots, skills, fetcher, extraction, transmissions);
    }
}
