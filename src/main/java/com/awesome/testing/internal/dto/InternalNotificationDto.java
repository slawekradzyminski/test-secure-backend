package com.awesome.testing.internal.dto;

import com.awesome.testing.dto.email.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * A customer email requested by warehouse-service. The backend resolves the address from the
 * username and renders the text from the template and its properties; a repeated requestId
 * is accepted without sending the email again.
 */
public record InternalNotificationDto(
        @NotNull(message = "requestId is required") UUID requestId,
        @NotBlank(message = "username is required") String username,
        @NotNull(message = "template is required") EmailTemplate template,
        @Size(max = 20, message = "at most 20 properties are allowed") Map<String, String> properties) {
}
