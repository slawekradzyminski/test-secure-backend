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
@Schema(description = "Replacement settings of a webhook subscription")
public class WebhookUpdateDto {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must be at most 2048 characters")
    @Schema(description = "Public http(s) endpoint on port 80 or 443, or inbox://<token> of one of your inboxes",
            example = "https://example.com/webhooks/shop", minLength = 1, pattern = "\\S", maxLength = 2048, requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @NotEmpty(message = "At least one event type is required")
    @Size(max = 20, message = "At most 20 event types are allowed")
    @ArraySchema(arraySchema = @Schema(description = "Event types to receive",
            example = "[\"order.created\"]", requiredMode = Schema.RequiredMode.REQUIRED), minItems = 1, maxItems = 20)
    private List<String> events;

    @Size(max = 255, message = "Description must be at most 255 characters")
    @Schema(types = {"string", "null"}, description = "Free-text label; null clears it", example = "Order sync",
            maxLength = 255)
    private String description;

    @Schema(types = {"boolean", "null"}, description = "Enable or disable deliveries; null keeps the current value. "
            + "Re-enabling an auto-disabled subscription resets its failure counter.", example = "true")
    private Boolean active;
}
