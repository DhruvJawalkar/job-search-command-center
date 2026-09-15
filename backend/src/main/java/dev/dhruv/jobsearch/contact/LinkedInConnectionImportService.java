package dev.dhruv.jobsearch.contact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class LinkedInConnectionImportService {
    private static final String HEADER_PREFIX = "First Name,Last Name,URL,";
    private static final DateTimeFormatter CONNECTION_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final Path sourceFile;
    private final LinkedInConnectionRepository connections;
    private final LinkedInConnectionImportBatchRepository batches;
    private final JobOpportunityRepository opportunities;
    private final ReferralCandidateRepository candidates;
    private final ReferralDiscoveryService referralDiscovery;

    public LinkedInConnectionImportService(
            @Value("${app.imports.linkedin-connections.file:../linkedin-data-import/Connections.csv}") String sourceFile,
            LinkedInConnectionRepository connections, LinkedInConnectionImportBatchRepository batches,
            JobOpportunityRepository opportunities, ReferralCandidateRepository candidates,
            ReferralDiscoveryService referralDiscovery) {
        this.sourceFile = Path.of(sourceFile).toAbsolutePath().normalize();
        this.connections = connections;
        this.batches = batches;
        this.opportunities = opportunities;
        this.candidates = candidates;
        this.referralDiscovery = referralDiscovery;
    }

    @Transactional
    public ImportView importConnections() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(sourceFile);
        } catch (IOException exception) {
            throw new IllegalStateException("LinkedIn connections export was not found at " + sourceFile + ".", exception);
        }
        String contentHash = sha256(bytes);
        var previous = batches.findByContentHash(contentHash);
        if (previous.isPresent()) return ImportView.from(previous.get(), true);

        LinkedInConnectionImportBatch batch = batches.save(
                new LinkedInConnectionImportBatch(sourceFile.getFileName().toString(), contentHash));
        try {
            List<Map<String, String>> records = parse(new String(bytes, StandardCharsets.UTF_8));
            int created = 0;
            int updated = 0;
            int skipped = 0;
            int rowNumber = 1;
            for (Map<String, String> record : records) {
                rowNumber++;
                String firstName = value(record, "First Name");
                String lastName = value(record, "Last Name");
                String fullName = (firstName + " " + lastName).trim();
                String profileUrl = value(record, "URL");
                if (fullName.isBlank()) {
                    skipped++;
                    continue;
                }
                String company = value(record, "Company");
                String role = value(record, "Position");
                String normalizedUrl = normalizeProfileUrl(profileUrl, fullName, company, role);
                LocalDate connectedOn = parseDate(value(record, "Connected On"));
                var existing = connections.findByNormalizedProfileUrl(normalizedUrl);
                if (existing.isPresent()) {
                    existing.get().update(fullName, company, normalizeCompany(company), role, profileUrl,
                            normalizedUrl, connectedOn, rowNumber, batch);
                    updated++;
                } else {
                    connections.save(new LinkedInConnection(fullName, company, normalizeCompany(company), role,
                            profileUrl, normalizedUrl, connectedOn, rowNumber, batch));
                    created++;
                }
            }
            batch.complete(records.size(), created, updated, skipped);
            return ImportView.from(batch, false);
        } catch (RuntimeException exception) {
            batch.fail(exception.getMessage());
            return ImportView.from(batch, false);
        }
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(connections.count(), batches.findFirstByOrderByStartedAtDesc()
                .map(batch -> ImportView.from(batch, false)).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ConnectionView> matches(UUID opportunityId) {
        var opportunity = opportunities.findById(opportunityId)
                .orElseThrow(() -> new NotFoundException("Opportunity " + opportunityId + " was not found."));
        return connections.findByNormalizedCompanyNameOrderByConnectedOnDescFullNameAsc(
                normalizeCompany(opportunity.getCompanyName())).stream().map(ConnectionView::from).toList();
    }

    @Transactional
    public ReferralCandidate saveAsCandidate(UUID connectionId, UUID opportunityId) {
        LinkedInConnection connection = connections.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("LinkedIn connection " + connectionId + " was not found."));
        var opportunity = opportunities.findById(opportunityId)
                .orElseThrow(() -> new NotFoundException("Opportunity " + opportunityId + " was not found."));
        if (connection.getProfileUrl() != null) {
            var existing = candidates.findByOpportunityIdAndProfileUrl(opportunityId, connection.getProfileUrl());
            if (existing.isPresent()) return existing.get();
        }
        int relevance = roleRelevance(connection.getRoleTitle());
        String note = "Imported from the official LinkedIn Connections.csv export"
                + (connection.getConnectedOn() == null ? "." : "; connected on " + connection.getConnectedOn() + ".");
        return referralDiscovery.create(new ReferralDiscoveryService.NewCandidate(opportunityId,
                connection.getFullName(), connection.getCompanyName(), connection.getRoleTitle(),
                connection.getProfileUrl(), ConnectionDegree.FIRST, ReferralChannel.LINKEDIN,
                RelationshipStrength.ACQUAINTANCE, null, null, true, false, relevance, 3, null, note));
    }

    private int roleRelevance(String role) {
        if (role == null) return 3;
        String normalized = role.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*\\b(software|engineer|engineering|platform|infrastructure|data|cloud|security|product)\\b.*")) return 4;
        if (normalized.matches(".*\\b(recruiter|talent|human resources|people partner|sourcer)\\b.*")) return 3;
        return 2;
    }

    private List<Map<String, String>> parse(String content) {
        int headerStart = content.indexOf(HEADER_PREFIX);
        if (headerStart < 0) throw new IllegalArgumentException("Connections.csv does not contain the expected LinkedIn header.");
        List<List<String>> rows = parseCsv(content.substring(headerStart));
        if (rows.size() < 2) return List.of();
        List<String> header = rows.getFirst();
        List<Map<String, String>> result = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> values = rows.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
            Map<String, String> record = new HashMap<>();
            for (int column = 0; column < header.size(); column++) {
                record.put(header.get(column).trim(), column < values.size() ? values.get(column).trim() : "");
            }
            result.add(record);
        }
        return result;
    }

    static List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') index++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        if (!quoted && (cell.length() > 0 || !row.isEmpty())) {
            row.add(cell.toString());
            rows.add(row);
        }
        if (quoted) throw new IllegalArgumentException("Connections.csv contains an unterminated quoted field.");
        return rows;
    }

    static String normalizeCompany(String value) {
        if (value == null || value.isBlank()) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replace("&", " and ").replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ")
                .replaceAll("\\b(incorporated|inc|llc|ltd|limited|corporation|corp|company|co|plc|private|pvt)\\b", "")
                .trim().replaceAll("\\s+", " ");
    }

    private String normalizeProfileUrl(String url, String fullName, String company, String role) {
        if (url != null && !url.isBlank()) return url.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return "missing:" + sha256((fullName + "|" + company + "|" + role).getBytes(StandardCharsets.UTF_8));
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value, CONNECTION_DATE);
    }

    private String value(Map<String, String> record, String key) {
        return record.getOrDefault(key, "").trim();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record Overview(long totalConnections, ImportView latestImport) {}
    public record ImportView(UUID id, String sourceFile, String contentHash,
            LinkedInConnectionImportBatch.Status status, int rowsSeen, int connectionsCreated,
            int connectionsUpdated, int rowsSkipped, String errorMessage, java.time.Instant startedAt,
            java.time.Instant completedAt, boolean replayed) {
        static ImportView from(LinkedInConnectionImportBatch batch, boolean replayed) {
            return new ImportView(batch.getId(), batch.getSourceFile(), batch.getContentHash(), batch.getStatus(),
                    batch.getRowsSeen(), batch.getConnectionsCreated(), batch.getConnectionsUpdated(),
                    batch.getRowsSkipped(), batch.getErrorMessage(), batch.getStartedAt(), batch.getCompletedAt(), replayed);
        }
    }
    public record ConnectionView(UUID id, String fullName, String companyName, String roleTitle,
            String profileUrl, LocalDate connectedOn) {
        static ConnectionView from(LinkedInConnection connection) {
            return new ConnectionView(connection.getId(), connection.getFullName(), connection.getCompanyName(),
                    connection.getRoleTitle(), connection.getProfileUrl(), connection.getConnectedOn());
        }
    }
}
