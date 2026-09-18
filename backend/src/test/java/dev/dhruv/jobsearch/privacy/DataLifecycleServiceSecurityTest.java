package dev.dhruv.jobsearch.privacy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import dev.dhruv.jobsearch.privacy.DataLifecycleInventoryService.Category;
import dev.dhruv.jobsearch.privacy.DataLifecycleOperation.OperationType;

class DataLifecycleServiceSecurityTest {

    @Test
    void partialFilesystemFailureRequiresANewPreviewInsteadOfReusingTheOldToken() throws Exception {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        String token = "preview-token-with-sufficient-length";
        DataLifecycleOperation operation = new DataLifecycleOperation(OperationType.DELETE_CATEGORIES,
                EnumSet.of(Category.SENSITIVE_WORKSPACE_FILES), "0".repeat(64), sha256(token),
                "DELETE 0 ROWS AND 1 FILES", 0, 1, 10, 0, now, now.plusSeconds(600));
        operation.markDatabaseDeleted(0);
        operation.markDeleteFinished(0, 1, now);
        DataLifecycleOperationRepository repository = mock(DataLifecycleOperationRepository.class);
        when(repository.findById(operation.getId())).thenReturn(java.util.Optional.of(operation));
        DataLifecycleService service = new DataLifecycleService(null, repository, null, null, null, null, null,
                Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.delete(operation.getId(), token, operation.getConfirmationPhrase()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fresh preview");
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
