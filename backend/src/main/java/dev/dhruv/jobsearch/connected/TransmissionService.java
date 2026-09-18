package dev.dhruv.jobsearch.connected;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;

@Service
public class TransmissionService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);

    private final TransmissionPreviewRepository previews;
    private final TransmissionReceiptRepository receipts;
    private final PrivacyPolicyService privacy;
    private final boolean connectedRuntime;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public TransmissionService(TransmissionPreviewRepository previews, TransmissionReceiptRepository receipts,
            PrivacyPolicyService privacy, @Value("${app.connected.enabled:false}") boolean connectedRuntime) {
        this(previews, receipts, privacy, connectedRuntime, Clock.systemUTC(), new SecureRandom());
    }

    TransmissionService(TransmissionPreviewRepository previews, TransmissionReceiptRepository receipts,
            PrivacyPolicyService privacy, boolean connectedRuntime, Clock clock, SecureRandom random) {
        this.previews = previews;
        this.receipts = receipts;
        this.privacy = privacy;
        this.connectedRuntime = connectedRuntime;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public PreviewView issue(TransmissionOperation operation, String destination, String purpose,
            List<String> minimizedFields, String canonicalPayload) {
        requireConnectedPolicy();
        String token = token();
        Instant now = clock.instant();
        String fields = minimizedFields.stream().map(String::trim).filter(value -> !value.isBlank())
                .distinct().sorted().reduce((left, right) -> left + "," + right).orElse("");
        TransmissionPreview preview = previews.save(new TransmissionPreview(sha256(token), operation,
                clean(destination, 500), clean(purpose, 240), clean(fields, 1000), sha256(canonicalPayload),
                now.plus(TOKEN_LIFETIME), now));
        return new PreviewView(preview.getId(), token, operation, preview.getDestination(), preview.getPurpose(),
                List.of(fields.split(",")).stream().filter(value -> !value.isBlank()).toList(), preview.getExpiresAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Consumption consume(String token, TransmissionOperation operation, String destination,
            String canonicalPayload) {
        requireConnectedPolicy();
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Preview and confirm this transmission first.");
        TransmissionPreview preview = previews.findByTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("The transmission confirmation is invalid."));
        if (preview.getOperation() != operation || !preview.getDestination().equals(destination)
                || !MessageDigest.isEqual(preview.getPayloadHash().getBytes(StandardCharsets.US_ASCII),
                        sha256(canonicalPayload).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("The data or destination changed after preview. Preview the transmission again.");
        }
        preview.consume(clock.instant());
        previews.save(preview);
        return new Consumption(preview.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReceiptView record(Consumption consumption, TransmissionOutcome outcome) {
        TransmissionPreview preview = previews.findById(consumption.previewId())
                .orElseThrow(() -> new IllegalStateException("The consumed transmission preview was not found."));
        TransmissionReceipt receipt = receipts.save(new TransmissionReceipt(preview, outcome, clock.instant()));
        return ReceiptView.from(receipt);
    }

    @Transactional(readOnly = true)
    public List<ReceiptView> recentReceipts() {
        return receipts.findTop25ByOrderByCompletedAtDesc().stream().map(ReceiptView::from).toList();
    }

    public boolean connectedRuntimeEnabled() { return connectedRuntime; }

    private void requireConnectedPolicy() {
        if (!connectedRuntime) throw new IllegalStateException("Start the reviewed connected runtime before transmitting data.");
        if (!privacy.connectedAssistanceEnabled()) {
            throw new IllegalStateException("Connected assistance is disabled in the local privacy policy.");
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String clean(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Transmission metadata is required.");
        String cleaned = value.trim();
        if (cleaned.length() > max) throw new IllegalArgumentException("Transmission metadata is too long.");
        return cleaned;
    }

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record PreviewView(UUID id, String confirmationToken, TransmissionOperation operation,
            String destination, String purpose, List<String> minimizedFields, Instant expiresAt) {}
    public record Consumption(UUID previewId) {}
    public record ReceiptView(UUID id, UUID previewId, TransmissionOperation operation, String destination,
            String purpose, List<String> minimizedFields, String payloadHash, String outcome,
            Instant createdAt, Instant completedAt) {
        static ReceiptView from(TransmissionReceipt value) {
            return new ReceiptView(value.getId(), value.getPreviewId(), value.getOperation(), value.getDestination(),
                    value.getPurpose(), List.of(value.getMinimizedFields().split(",")).stream()
                            .filter(field -> !field.isBlank()).toList(),
                    value.getPayloadHash(), value.getOutcome().name(), value.getCreatedAt(), value.getCompletedAt());
        }
    }
}
