package com.awesome.testing.dto.webhook;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "New webhook subscription")
public class WebhookCreateDto {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must be at most 2048 characters")
    @Schema(description = "Public http(s) endpoint on port 80 or 443, or inbox://<token> of one of your inboxes",
            example = "https://example.com/webhooks/shop", minLength = 1, pattern = "\\S", maxLength = 2048, requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @NotEmpty(message = "At least one event type is required")
    @Size(max = 20, message = "At most 20 event types are allowed")
    @ArraySchema(arraySchema = @Schema(description = "Event types to receive; see GET /api/v1/webhooks/event-types",
            example = "[\"order.created\",\"order.cancelled\"]", requiredMode = Schema.RequiredMode.REQUIRED),
            minItems = 1, maxItems = 20)
    private List<String> events;

    @Size(max = 255, message = "Description must be at most 255 characters")
    @Schema(types = {"string", "null"}, description = "Free-text label", example = "Order sync", maxLength = 255)
    private String description;
}
