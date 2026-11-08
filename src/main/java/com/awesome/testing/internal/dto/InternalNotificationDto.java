package com.awesome.testing.internal.dto;

import com.awesome.testing.dto.email.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** A customer email requested by warehouse-service; the backend resolves the address from the username. */
public record InternalNotificationDto(
        @NotBlank(message = "username is required") String username,
        @NotNull(message = "template is required") EmailTemplate template,
        @NotBlank(message = "subject is required") @Size(max = 255, message = "subject must be at most 255 characters")
        String subject,
        @NotBlank(message = "message is required") @Size(max = 5000, message = "message must be at most 5000 characters")
        String message,
        Map<String, String> properties) {
}
