package com.awesome.testing.controller.email;

import com.awesome.testing.service.password.LocalEmailOutbox;
import com.awesome.testing.config.properties.PasswordResetProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "aitesters"})
@RequiredArgsConstructor
@RequestMapping("/api/v1/local/email/outbox")
@Tag(name = "local-email-outbox", description = "Helpers for inspecting the local JMS stub")
public class LocalEmailOutboxController {

    private static final String OUTBOX_ACCESS_KEY_HEADER = "X-Local-Outbox-Key";

    private final LocalEmailOutbox localEmailOutbox;
    private final PasswordResetProperties passwordResetProperties;

    @PostConstruct
    void validateConfiguration() {
        if (passwordResetProperties.isRequireOutboxAccessKey()
                && passwordResetProperties.getOutboxAccessKey().isBlank()) {
            throw new IllegalStateException(
                    "password-reset.outbox-access-key must be configured when protected outbox access is enabled"
            );
        }
    }

    @GetMapping
    @Operation(summary = "Fetch all queued emails produced in the local profile",
            description = "Returns messages captured by the local email outbox stub for development and test inspection.")
    @ApiResponse(responseCode = "200", description = "Queued local emails returned successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LocalEmailOutbox.StoredEmail>> getOutbox(
            @RequestHeader(name = OUTBOX_ACCESS_KEY_HEADER, required = false) String accessKey) {
        requireAccessKey(accessKey);
        return ResponseEntity.ok(localEmailOutbox.getAll());
    }

    @DeleteMapping
    @Operation(summary = "Clear the local outbox buffer",
            description = "Deletes all messages currently stored in the local email outbox buffer.")
    @ApiResponse(responseCode = "200", description = "Outbox buffer cleared successfully")
    @ApiResponse(responseCode = "500", description = "Unexpected error while clearing outbox")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> clearOutbox(
            @RequestHeader(name = OUTBOX_ACCESS_KEY_HEADER, required = false) String accessKey) {
        requireAccessKey(accessKey);
        localEmailOutbox.clear();
        return ResponseEntity.ok().build();
    }

    private void requireAccessKey(String suppliedAccessKey) {
        if (!passwordResetProperties.isRequireOutboxAccessKey()) {
            return;
        }
        byte[] expected = passwordResetProperties.getOutboxAccessKey().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedAccessKey == null
                ? new byte[0]
                : suppliedAccessKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid local outbox access key");
        }
    }
}
