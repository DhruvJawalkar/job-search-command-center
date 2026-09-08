package dev.dhruv.jobsearch.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CompanySegmentCatalog {

    static final String UNCLASSIFIED = "Unclassified";

    private final Map<String, String> segments;

    CompanySegmentCatalog(
            @Value("${app.cohorts.company-targets.folder:..}") String folder,
            @Value("${app.cohorts.company-targets.filename-pattern:*_Target_Companies.xlsx}") String pattern) {
        this.segments = load(Path.of(folder).toAbsolutePath().normalize(), pattern);
    }

    String segmentFor(String companyName) {
        return segments.getOrDefault(normalize(companyName), UNCLASSIFIED);
    }

    private static Map<String, String> load(Path folder, String pattern) {
        if (!Files.isDirectory(folder)) return Map.of();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, pattern)) {
            for (Path file : files) return readWorkbook(file);
        } catch (IOException ignored) {
            // Missing local research data must not prevent the application from starting.
        }
        return Map.of();
    }

    private static Map<String, String> readWorkbook(Path file) {
        Map<String, String> result = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (InputStream input = Files.newInputStream(file); var workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet("Top 100");
            if (sheet == null) sheet = workbook.getSheetAt(0);
            int headerRow = findHeaderRow(sheet, formatter);
            if (headerRow < 0) return Map.of();
            Row header = sheet.getRow(headerRow);
            int companyColumn = findColumn(header, formatter, "Company");
            int categoryColumn = findColumn(header, formatter, "Category");
            if (companyColumn < 0 || categoryColumn < 0) return Map.of();
            for (int index = headerRow + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String company = formatter.formatCellValue(row.getCell(companyColumn)).trim();
                String category = formatter.formatCellValue(row.getCell(categoryColumn)).trim();
                if (company.isBlank() || category.isBlank()) continue;
                result.put(normalize(company), category);
                for (String alias : company.split("/")) {
                    if (!alias.isBlank()) result.putIfAbsent(normalize(alias), category);
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.copyOf(result);
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int limit = Math.min(sheet.getLastRowNum(), 20);
        for (int index = sheet.getFirstRowNum(); index <= limit; index++) {
            Row row = sheet.getRow(index);
            if (row != null && findColumn(row, formatter, "Company") >= 0
                    && findColumn(row, formatter, "Category") >= 0) return index;
        }
        return -1;
    }

    private static int findColumn(Row row, DataFormatter formatter, String name) {
        for (int index = 0; index < row.getLastCellNum(); index++) {
            if (name.equalsIgnoreCase(formatter.formatCellValue(row.getCell(index)).trim())) return index;
        }
        return -1;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
