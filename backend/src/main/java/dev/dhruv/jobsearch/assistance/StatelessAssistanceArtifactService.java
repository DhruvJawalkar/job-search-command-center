package dev.dhruv.jobsearch.assistance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Produces response-carried, process-bound save artifacts for Stateless mode.
 * The signed payload is never stored by the application and becomes invalid on
 * expiry or service restart (when the random signing key changes).
 */
@Component
class StatelessAssistanceArtifactService {

    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Clock clock;
    private final byte[] key;

    @Autowired
    StatelessAssistanceArtifactService(ObjectMapper objectMapper,
            @Value("${app.privacy.stateless-save-artifact.ttl:PT5M}") Duration ttl) {
        this(objectMapper, ttl, Clock.systemUTC(), randomKey());
    }

    StatelessAssistanceArtifactService(ObjectMapper objectMapper, Duration ttl, Clock clock, byte[] key) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("Stateless save artifact TTL must be between zero and 15 minutes.");
        }
        if (key == null || key.length < 32) throw new IllegalArgumentException("A 256-bit signing key is required.");
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.clock = clock;
        this.key = key.clone();
    }

    String issue(AssistanceUseCase useCase, UUID targetId, String inputHash, String resultPayload) {
        try {
            Payload value = new Payload(useCase, targetId, inputHash, resultPayload, clock.instant().plus(ttl));
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
            return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not create the response-only save artifact.", exception);
        }
    }

    Payload verify(String artifact, AssistanceUseCase expectedUseCase, UUID expectedTargetId, String currentInputHash) {
        try {
            if (artifact == null || artifact.isBlank()) throw invalid();
            String[] parts = artifact.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]),
                    Base64.getUrlDecoder().decode(parts[1]))) throw invalid();
            Payload payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Payload.class);
            if (payload.useCase() != expectedUseCase || !payload.targetId().equals(expectedTargetId)
                    || !MessageDigest.isEqual(payload.inputHash().getBytes(StandardCharsets.US_ASCII),
                            currentInputHash.getBytes(StandardCharsets.US_ASCII))) throw invalid();
            if (!payload.expiresAt().isAfter(clock.instant())) {
                throw new IllegalStateException("The response-only save artifact has expired. Generate a new suggestion.");
            }
            return payload;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available.", exception);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("The response-only save artifact is invalid. Generate a new suggestion.");
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    record Payload(AssistanceUseCase useCase, UUID targetId, String inputHash, String resultPayload,
            Instant expiresAt) {}
}
