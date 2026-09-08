package dev.dhruv.jobsearch.ingestion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import dev.dhruv.jobsearch.ingestion.ImportBatch.ImportCounts;
import dev.dhruv.jobsearch.ingestion.OpportunityObservation.ImportedObservation;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;

@Component
public class DailyHighFitFileImporter {

    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Kolkata");
    static final String IMPORTER_VERSION = "daily-high-fit-v2";
    private static final String SHEET_NAME = "High-Fit Openings";
    private static final List<String> REQUIRED_HEADERS = List.of(
            "Rank", "Company", "Exact Title", "Location / Work Arrangement", "Posting Date",
            "Overall Fit", "Recruiter-Screen Strength", "Technical Scope", "Growth Potential",
            "Weighted Total", "Recommendation", "Role Summary", "Fit Rationale", "Key Risks / Gaps",
            "Direct Job Link", "Recommended Resume Variant", "Authorization / Eligibility", "Verified Date");
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "(?ms)^\\s*(\\d+)[.)]\\s+(.*?)(?=\\R\\s*\\d+[.)]\\s+|\\z)");

    private final ImportBatchRepository batchRepository;
    private final OpportunityObservationRepository observationRepository;
    private final DailyPriorityActionRepository actionRepository;
    private final JobOpportunityRepository opportunityRepository;
    private final ResumeVariantRepository resumeRepository;
    private final ObjectMapper objectMapper;

    public DailyHighFitFileImporter(ImportBatchRepository batchRepository,
            OpportunityObservationRepository observationRepository,
            DailyPriorityActionRepository actionRepository,
            JobOpportunityRepository opportunityRepository,
            ResumeVariantRepository resumeRepository,
            ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.observationRepository = observationRepository;
        this.actionRepository = actionRepository;
        this.opportunityRepository = opportunityRepository;
        this.resumeRepository = resumeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportBatch importFile(Path workbookPath, Path actionPath, LocalDate sourceDate, String contentHash)
            throws IOException {
        ImportBatch batch = batchRepository.save(new ImportBatch(
                workbookPath.getFileName().toString(),
                actionPath == null ? null : actionPath.getFileName().toString(),
                sourceDate,
                contentHash));

        List<ImportedRole> rows = parseWorkbook(workbookPath, sourceDate);
        int opportunitiesCreated = 0;
        int opportunitiesUpdated = 0;
        int observationsCreated = 0;
        int observationsUpdated = 0;
        Map<String, ResumeVariant> resumeCache = new LinkedHashMap<>();
        List<JobOpportunity> dayOpportunities = new ArrayList<>();

        for (ImportedRole row : rows) {
            String canonicalUrl = canonicalize(row.directJobLink());
            Optional<JobOpportunity> existing = opportunityRepository.findByCanonicalUrl(canonicalUrl);
            JobOpportunity opportunity;
            if (existing.isPresent()) {
                opportunity = existing.get();
                opportunitiesUpdated++;
            } else {
                opportunity = new JobOpportunity(
                        row.company(), row.exactTitle(), row.location(), detectWorkMode(row.location()),
                        "Daily high-fit workbook", row.directJobLink(), canonicalUrl, row.roleSummary(),
                        sourceDate.atStartOfDay(USER_ZONE).toInstant());
                opportunitiesCreated++;
            }

            int fitScore = row.weightedTotal().multiply(BigDecimal.TEN)
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            opportunity.refreshFromImport(
                    row.company(), row.exactTitle(), row.location(), detectWorkMode(row.location()),
                    "Daily high-fit workbook", row.directJobLink(), row.roleSummary(), fitScore,
                    row.fitRationale(), suggestedStatus(row.recommendation()));
            opportunity = opportunityRepository.save(opportunity);
            dayOpportunities.add(opportunity);

            ResumeVariant resume = null;
            if (row.recommendedResumeName() != null) {
                resume = resumeCache.computeIfAbsent(row.recommendedResumeName().toLowerCase(Locale.ROOT), ignored ->
                        resumeRepository.findByNameIgnoreCase(row.recommendedResumeName())
                                .orElseGet(() -> resumeRepository.save(new ResumeVariant(
                                        row.recommendedResumeName(), row.recommendedResumeName(),
                                        "Imported reference", null, null))));
            }

            Optional<OpportunityObservation> existingObservation =
                    observationRepository.findByOpportunityIdAndObservedOn(opportunity.getId(), sourceDate);
            OpportunityObservation observation = existingObservation.isPresent()
                    ? existingObservation.get()
                    : new OpportunityObservation(batch, opportunity, sourceDate);
            observation.refresh(batch, resume, row.observation());
            observationRepository.save(observation);
            if (existingObservation.isPresent()) observationsUpdated++; else observationsCreated++;
        }

        int actionsCreated = 0;
        int actionsUpdated = 0;
        if (actionPath != null && Files.isRegularFile(actionPath)) {
            for (ImportedAction importedAction : parseActions(actionPath)) {
                Optional<DailyPriorityAction> existingAction = actionRepository
                        .findByActionDateAndPriorityRank(sourceDate, importedAction.rank());
                DailyPriorityAction action = existingAction
                        .orElseGet(() -> new DailyPriorityAction(batch, sourceDate, importedAction.rank()));
                action.refresh(batch, linkAction(importedAction.text(), dayOpportunities).orElse(null),
                        importedAction.text(), actionPath.getFileName().toString());
                actionRepository.save(action);
                if (existingAction.isPresent()) actionsUpdated++; else actionsCreated++;
            }
        }

        batch.complete(new ImportCounts(rows.size(), opportunitiesCreated, opportunitiesUpdated,
                observationsCreated, observationsUpdated, actionsCreated, actionsUpdated));
        return batch;
    }

    @Transactional
    public ImportBatch recordFailure(Path workbookPath, Path actionPath, LocalDate sourceDate,
            String contentHash, String message) {
        ImportBatch batch = new ImportBatch(workbookPath.getFileName().toString(),
                actionPath == null ? null : actionPath.getFileName().toString(), sourceDate, contentHash);
        batch.fail(message);
        return batchRepository.save(batch);
    }

    private List<ImportedRole> parseWorkbook(Path workbookPath, LocalDate sourceDate) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(workbookPath.toFile(), null, true)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Required sheet '" + SHEET_NAME + "' was not found.");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row headerRow = findHeaderRow(sheet, formatter, evaluator);
            Map<String, Integer> columns = headerColumns(headerRow, formatter, evaluator);
            List<String> missing = REQUIRED_HEADERS.stream().filter(header -> !columns.containsKey(header)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Missing required headers: " + String.join(", ", missing));
            }

            List<ImportedRole> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || numeric(row, columns.get("Rank"), evaluator) == null) continue;
                rows.add(readRole(row, columns, formatter, evaluator, sourceDate));
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("No ranked opportunity rows were found.");
            }
            return rows;
        }
    }

    private ImportedRole readRole(Row row, Map<String, Integer> columns, DataFormatter formatter,
            FormulaEvaluator evaluator, LocalDate sourceDate) {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String header : REQUIRED_HEADERS) {
            Cell cell = row.getCell(columns.get(header));
            raw.put(header, rawValue(cell, formatter, evaluator));
        }
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(raw);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize source row " + (row.getRowNum() + 1), exception);
        }

        int rank = requiredDecimal(row, columns, "Rank", evaluator).intValue();
        String company = requiredText(row, columns, "Company", formatter, evaluator);
        String title = requiredText(row, columns, "Exact Title", formatter, evaluator);
        String location = text(row, columns, "Location / Work Arrangement", formatter, evaluator);
        String recommendation = requiredText(row, columns, "Recommendation", formatter, evaluator);
        String roleSummary = requiredText(row, columns, "Role Summary", formatter, evaluator);
        String fitRationale = requiredText(row, columns, "Fit Rationale", formatter, evaluator);
        String directLink = requiredText(row, columns, "Direct Job Link", formatter, evaluator);
        String resumeName = text(row, columns, "Recommended Resume Variant", formatter, evaluator);
        LocalDate verifiedDate = date(row, columns.get("Verified Date"), evaluator);
        if (verifiedDate == null) verifiedDate = sourceDate;

        ImportedObservation observation = new ImportedObservation(
                rank,
                date(row, columns.get("Posting Date"), evaluator),
                verifiedDate,
                requiredDecimal(row, columns, "Overall Fit", evaluator),
                requiredDecimal(row, columns, "Recruiter-Screen Strength", evaluator),
                requiredDecimal(row, columns, "Technical Scope", evaluator),
                requiredDecimal(row, columns, "Growth Potential", evaluator),
                requiredDecimal(row, columns, "Weighted Total", evaluator),
                recommendation,
                roleSummary,
                fitRationale,
                text(row, columns, "Key Risks / Gaps", formatter, evaluator),
                text(row, columns, "Authorization / Eligibility", formatter, evaluator),
                resumeName,
                sha256(rawPayload.getBytes(StandardCharsets.UTF_8)),
                rawPayload);
        return new ImportedRole(company, title, location, directLink, recommendation, roleSummary,
                fitRationale, resumeName, observation.weightedTotal(), observation);
    }

    private Row findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (Row row : sheet) {
            if ("Rank".equals(formatted(row.getCell(0), formatter, evaluator))) return row;
        }
        throw new IllegalArgumentException("The Rank header row was not found.");
    }

    private Map<String, Integer> headerColumns(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : row) {
            String value = formatted(cell, formatter, evaluator);
            if (value != null) result.put(value, cell.getColumnIndex());
        }
        return result;
    }

    private List<ImportedAction> parseActions(Path actionPath) throws IOException {
        String content = Files.readString(actionPath, StandardCharsets.UTF_8);
        Matcher matcher = ACTION_PATTERN.matcher(content);
        List<ImportedAction> actions = new ArrayList<>();
        while (matcher.find()) {
            actions.add(new ImportedAction(Integer.parseInt(matcher.group(1)),
                    matcher.group(2).replaceAll("\\s+", " ").trim()));
        }
        return actions;
    }

    private Optional<JobOpportunity> linkAction(String actionText, List<JobOpportunity> opportunities) {
        String normalizedAction = normalize(actionText);
        List<JobOpportunity> companyMatches = opportunities.stream()
                .filter(opportunity -> normalizedAction.contains(normalize(opportunity.getCompanyName())))
                .toList();
        if (companyMatches.size() == 1) return Optional.of(companyMatches.getFirst());
        List<JobOpportunity> titleMatches = companyMatches.stream()
                .filter(opportunity -> normalizedAction.contains(normalize(opportunity.getRoleTitle())))
                .toList();
        return titleMatches.size() == 1 ? Optional.of(titleMatches.getFirst()) : Optional.empty();
    }

    private String requiredText(Row row, Map<String, Integer> columns, String header,
            DataFormatter formatter, FormulaEvaluator evaluator) {
        String value = text(row, columns, header, formatter, evaluator);
        if (value == null) throw new IllegalArgumentException(header + " is blank on row " + (row.getRowNum() + 1));
        return value;
    }

    private String text(Row row, Map<String, Integer> columns, String header,
            DataFormatter formatter, FormulaEvaluator evaluator) {
        return formatted(row.getCell(columns.get(header)), formatter, evaluator);
    }

    private String formatted(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        String value = formatter.formatCellValue(cell, evaluator).trim();
        return value.isBlank() ? null : value;
    }

    private BigDecimal requiredDecimal(Row row, Map<String, Integer> columns, String header,
            FormulaEvaluator evaluator) {
        BigDecimal value = numeric(row, columns.get(header), evaluator);
        if (value == null) throw new IllegalArgumentException(header + " is blank on row " + (row.getRowNum() + 1));
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal numeric(Row row, int column, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.FORMULA) {
            CellValue value = evaluator.evaluate(cell);
            if (value != null && value.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(value.getNumberValue());
            }
        }
        String text = cell.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate date(Row row, int column, FormulaEvaluator evaluator) {
        BigDecimal value = numeric(row, column, evaluator);
        return value == null ? null : DateUtil.getLocalDateTime(value.doubleValue()).toLocalDate();
    }

    private Object rawValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.FORMULA) {
            CellValue value = evaluator.evaluate(cell);
            if (value != null && value.getCellType() == CellType.NUMERIC) return value.getNumberValue();
        }
        return formatted(cell, formatter, evaluator);
    }

    private OpportunityStatus suggestedStatus(String recommendation) {
        String normalized = recommendation.toLowerCase(Locale.ROOT);
        if (normalized.contains("skip")) return OpportunityStatus.SKIPPED;
        if (normalized.contains("apply now") || normalized.contains("referral")) return OpportunityStatus.SHORTLISTED;
        return OpportunityStatus.REVIEWING;
    }

    private WorkMode detectWorkMode(String location) {
        if (location == null) return WorkMode.UNSPECIFIED;
        String normalized = location.toLowerCase(Locale.ROOT);
        if (normalized.contains("remote")) return WorkMode.REMOTE;
        if (normalized.contains("hybrid")) return WorkMode.HYBRID;
        if (normalized.contains("on-site") || normalized.contains("onsite")) return WorkMode.ONSITE;
        return WorkMode.UNSPECIFIED;
    }

    private String canonicalize(String sourceUrl) {
        try {
            URI uri = new URI(sourceUrl.trim());
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return new URI(scheme, null, host, uri.getPort(), path, null, null).toString();
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid job URL: " + sourceUrl, exception);
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private record ImportedRole(String company, String exactTitle, String location, String directJobLink,
            String recommendation, String roleSummary, String fitRationale, String recommendedResumeName,
            BigDecimal weightedTotal, ImportedObservation observation) {
    }

    private record ImportedAction(int rank, String text) {
    }
}
