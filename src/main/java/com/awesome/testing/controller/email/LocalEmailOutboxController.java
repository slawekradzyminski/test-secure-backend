package com.awesome.testing.controller.email;

import com.awesome.testing.service.password.LocalEmailOutbox;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping("/local/email/outbox")
@Tag(name = "local-email-outbox", description = "Helpers for inspecting the local JMS stub")
public class LocalEmailOutboxController {

    private final LocalEmailOutbox localEmailOutbox;

    @GetMapping
    @Operation(summary = "Fetch all queued emails produced in the local profile")
    public ResponseEntity<List<LocalEmailOutbox.StoredEmail>> getOutbox() {
        return ResponseEntity.ok(localEmailOutbox.getAll());
    }

    @DeleteMapping
    @Operation(summary = "Clear the local outbox buffer")
    public ResponseEntity<Void> clearOutbox() {
        localEmailOutbox.clear();
        return ResponseEntity.noContent().build();
    }
}
