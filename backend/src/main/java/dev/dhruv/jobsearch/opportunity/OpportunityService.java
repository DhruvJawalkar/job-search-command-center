package dev.dhruv.jobsearch.opportunity;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class OpportunityService {

    private final JobOpportunityRepository repository;
    private final boolean demoMode;

    public OpportunityService(JobOpportunityRepository repository,
            @Value("${app.demo-mode:false}") boolean demoMode) {
        this.repository = repository;
        this.demoMode = demoMode;
    }

    @Transactional
    public JobOpportunity create(CreateOpportunity command) {
        String canonicalUrl = canonicalize(command.sourceUrl());
        if (canonicalUrl != null && repository.findByCanonicalUrl(canonicalUrl).isPresent()) {
            throw new IllegalStateException("This job URL is already in the opportunity inbox.");
        }
        return repository.save(new JobOpportunity(
                command.companyName().trim(),
                command.roleTitle().trim(),
                trimToNull(command.location()),
                command.workMode(),
                trimToNull(command.sourceName()),
                trimToNull(command.sourceUrl()),
                canonicalUrl,
                trimToNull(command.description()),
                command.discoveredAt()));
    }

    @Transactional(readOnly = true)
    public List<JobOpportunity> list() {
        return demoMode ? repository.findAllByOrderByDiscoveredAtDesc()
                : repository.findByDemoFalseOrderByDiscoveredAtDesc();
    }

    @Transactional(readOnly = true)
    public JobOpportunity get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Opportunity " + id + " was not found."));
    }

    @Transactional
    public JobOpportunity decide(UUID id, OpportunityStatus status, Integer fitScore, String fitSummary) {
        JobOpportunity opportunity = get(id);
        opportunity.recordDecision(status, fitScore, trimToNull(fitSummary));
        return opportunity;
    }

    @Transactional
    public JobOpportunity archive(UUID id, String reason) {
        JobOpportunity opportunity = get(id);
        opportunity.archive(reason);
        return opportunity;
    }

    @Transactional
    public JobOpportunity restore(UUID id) {
        JobOpportunity opportunity = get(id);
        opportunity.restore();
        return opportunity;
    }

    @Transactional(readOnly = true)
    public JobOpportunity findByCanonicalUrl(String sourceUrl) {
        String canonical = canonicalize(sourceUrl);
        return canonical == null ? null : repository.findByCanonicalUrl(canonical).orElse(null);
    }

    @Transactional(readOnly = true)
    public JobOpportunity findBySourceExternalId(String sourceName, String sourceExternalId) {
        String source = trimToNull(sourceName);
        String externalId = trimToNull(sourceExternalId);
        if (source == null || externalId == null) return null;
        return repository.findBySourceNameIgnoreCaseAndSourceExternalId(source, externalId).orElse(null);
    }

    @Transactional
    public JobOpportunity recordSourceExternalId(UUID id, String sourceExternalId) {
        JobOpportunity opportunity = get(id);
        opportunity.recordSourceExternalId(trimToNull(sourceExternalId));
        return opportunity;
    }

    @Transactional
    public JobOpportunity mergeReviewed(UUID id, MergeOpportunity command) {
        JobOpportunity opportunity = get(id);
        Set<OpportunityMergeField> fields = command.fields() == null ? Set.of() : Set.copyOf(command.fields());
        if (fields.isEmpty()) throw new IllegalArgumentException("Choose at least one field to merge.");
        String company = trimToNull(command.companyName());
        String title = trimToNull(command.roleTitle());
        if (fields.contains(OpportunityMergeField.COMPANY_NAME) && company == null) {
            throw new IllegalArgumentException("Company cannot be cleared during merge.");
        }
        if (fields.contains(OpportunityMergeField.ROLE_TITLE) && title == null) {
            throw new IllegalArgumentException("Role title cannot be cleared during merge.");
        }
        String canonical = fields.contains(OpportunityMergeField.SOURCE_URL) ? canonicalize(command.sourceUrl()) : null;
        if (canonical != null) {
            var duplicate = repository.findByCanonicalUrl(canonical);
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                throw new IllegalStateException("The selected source URL belongs to a different tracked opening.");
            }
        }
        opportunity.mergeReviewedFields(fields, company, title, trimToNull(command.location()), command.workMode(),
                trimToNull(command.sourceName()), trimToNull(command.sourceUrl()), canonical,
                trimToNull(command.sourceExternalId()), trimToNull(command.description()));
        return opportunity;
    }

    private String canonicalize(String sourceUrl) {
        String value = trimToNull(sourceUrl);
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return value;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, null, host, uri.getPort(), path, null, null).toString();
        } catch (URISyntaxException exception) {
            return value;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CreateOpportunity(
            String companyName,
            String roleTitle,
            String location,
            WorkMode workMode,
            String sourceName,
            String sourceUrl,
            String description,
            Instant discoveredAt) {
    }

    public record MergeOpportunity(Set<OpportunityMergeField> fields, String companyName, String roleTitle,
            String location, WorkMode workMode, String sourceName, String sourceUrl, String sourceExternalId,
            String description) {}
}
