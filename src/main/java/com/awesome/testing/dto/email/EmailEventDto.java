package com.awesome.testing.dto.email;

import com.awesome.testing.entity.EmailEventEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEventDto {

    @Schema(description = "Email template or event type")
    EmailTemplate type;

    @Schema(description = "Delivery status recorded by the application for the Mailhog-backed test sink")
    EmailDeliveryStatus status;

    @Schema(description = "Masked recipient email address", example = "j***@example.com")
    String recipientMasked;

    @Schema(description = "When the email event was first recorded")
    Instant createdAt;

    @Schema(description = "When the email event status was last updated")
    Instant updatedAt;

    @Schema(description = "Failure detail when the test mail sink handoff did not succeed")
    String failureReason;

    public static EmailEventDto from(EmailEventEntity entity) {
        return EmailEventDto.builder()
                .type(entity.getType())
                .status(entity.getStatus())
                .recipientMasked(maskRecipient(entity.getRecipientEmail()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .failureReason(entity.getFailureReason())
                .build();
    }

    private static String maskRecipient(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
