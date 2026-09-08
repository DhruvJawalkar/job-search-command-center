package dev.dhruv.jobsearch.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ApplicationArtifactService {

    private static final long MAX_RESUME_BYTES = 15L * 1024 * 1024;
    private static final int MAX_JOB_DESCRIPTION_CHARACTERS = 500_000;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ApplicationArtifactRepository repository;
    private final Path storageRoot;

    public ApplicationArtifactService(ApplicationArtifactRepository repository,
            @Value("${app.application-artifacts.folder:../application-resumes}") String storageFolder) {
        this.repository = repository;
        this.storageRoot = Path.of(storageFolder).toAbsolutePath().normalize();
    }

    public void validate(MultipartFile resumeFile, String jobDescription) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new IllegalArgumentException("Upload the exact PDF resume used for this application.");
        }
        if (resumeFile.getSize() > MAX_RESUME_BYTES) {
            throw new IllegalArgumentException("The resume PDF must be 15 MB or smaller.");
        }
        String originalName = resumeFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("The application resume must be a PDF file.");
        }
        if (jobDescription != null && jobDescription.length() > MAX_JOB_DESCRIPTION_CHARACTERS) {
            throw new IllegalArgumentException("The job description must be 500,000 characters or fewer.");
        }
    }

    public List<ApplicationArtifact> store(JobApplication application, MultipartFile resumeFile,
            String jobDescription) {
        validate(resumeFile, jobDescription);
        List<Path> writtenPaths = new ArrayList<>();
        try {
            byte[] resumeBytes = resumeFile.getBytes();
            validatePdfHeader(resumeBytes);
            Path companyDirectory = resolveCompanyDirectory(application.getOpportunity().getCompanyName());
            Files.createDirectories(companyDirectory);
            String roleSummary = safeSegment(application.getOpportunity().getRoleTitle(), "Role", 64, true);

            Path resumePath = uniqueTarget(companyDirectory, "Submitted_Resume_" + roleSummary, ".pdf",
                    application.getId());
            writeNew(resumePath, resumeBytes);
            writtenPaths.add(resumePath);

            List<ApplicationArtifact> artifacts = new ArrayList<>();
            artifacts.add(new ApplicationArtifact(application, ApplicationArtifactType.RESUME_PDF,
                    resumeFile.getOriginalFilename(), relativePath(resumePath), sha256(resumeBytes),
                    "application/pdf", resumeBytes.length));

            if (jobDescription != null && !jobDescription.isBlank()) {
                byte[] descriptionBytes = jobDescription.getBytes(StandardCharsets.UTF_8);
                Path descriptionPath = uniqueTarget(companyDirectory, "JobDescription_" + roleSummary, ".txt",
                        application.getId());
                writeNew(descriptionPath, descriptionBytes);
                writtenPaths.add(descriptionPath);
                artifacts.add(new ApplicationArtifact(application, ApplicationArtifactType.JOB_DESCRIPTION_TEXT,
                        null, relativePath(descriptionPath), sha256(descriptionBytes),
                        "text/plain; charset=UTF-8", descriptionBytes.length));
            }

            registerRollbackCleanup(writtenPaths);
            return repository.saveAll(artifacts);
        } catch (IOException exception) {
            deleteQuietly(writtenPaths);
            throw new IllegalStateException("Could not preserve the application files: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            deleteQuietly(writtenPaths);
            throw exception;
        }
    }

    public List<ApplicationArtifact> list(UUID applicationId) {
        return repository.findByApplicationIdOrderByArtifactTypeAsc(applicationId);
    }

    Path storageRoot() { return storageRoot; }

    private void validatePdfHeader(byte[] content) {
        if (content.length < PDF_HEADER.length) {
            throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
        }
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (content[index] != PDF_HEADER[index]) {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            }
        }
    }

    private Path resolveCompanyDirectory(String companyName) {
        String folderName = safeSegment(companyName, "Unknown Company", 64, false);
        Path directory = storageRoot.resolve(folderName).normalize();
        if (!directory.startsWith(storageRoot)) {
            throw new IllegalArgumentException("The company name cannot be used as a storage folder.");
        }
        return directory;
    }

    private String safeSegment(String value, String fallback, int maxLength, boolean underscores) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", " ")
                .replaceAll("\\s+", underscores ? "_" : " ")
                .replaceAll("[. ]+$", "")
                .trim();
        if (normalized.isBlank()) normalized = fallback;
        if (isWindowsReservedName(normalized)) normalized = "_" + normalized;
        if (normalized.length() > maxLength) normalized = normalized.substring(0, maxLength).replaceAll("[. _]+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private boolean isWindowsReservedName(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        return upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    }

    private Path uniqueTarget(Path directory, String baseName, String extension, UUID applicationId) {
        Path preferred = directory.resolve(baseName + extension).normalize();
        if (!preferred.startsWith(directory)) throw new IllegalArgumentException("Invalid application filename.");
        if (!Files.exists(preferred)) return preferred;
        String suffix = "_" + applicationId.toString().substring(0, 8);
        Path candidate = directory.resolve(baseName + suffix + extension).normalize();
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + suffix + "_" + counter++ + extension).normalize();
        }
        return candidate;
    }

    private void writeNew(Path target, byte[] content) throws IOException {
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private String relativePath(Path target) {
        return storageRoot.relativize(target).toString().replace('\\', '/');
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void registerRollbackCleanup(List<Path> writtenPaths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        List<Path> paths = List.copyOf(writtenPaths);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(paths);
            }
        });
    }

    private void deleteQuietly(List<Path> paths) {
        for (Path path : paths) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }
}
