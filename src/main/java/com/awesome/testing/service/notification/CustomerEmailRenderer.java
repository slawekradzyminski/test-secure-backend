package com.awesome.testing.service.notification;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the commerce emails. The backend owns every customer email text: warehouse-service
 * sends only a template name and its properties, and the JMS consumer only delivers the
 * subject and body rendered here (the local outbox shows the same text without a consumer).
 */
@Component
public class CustomerEmailRenderer {

    static final Map<EmailTemplate, List<String>> REQUIRED_PROPERTIES = Map.of(
            EmailTemplate.ORDER_SHIPPED, List.of("orderId", "carrier", "trackingNumber"),
            EmailTemplate.ORDER_DELIVERED, List.of("orderId"),
            EmailTemplate.RETURN_UPDATED, List.of("returnId", "orderId", "status"),
            EmailTemplate.BACK_IN_STOCK, List.of("productId", "productName"));

    /** Rejects missing template properties with 400, before anything is queued. */
    public void validate(EmailTemplate template, Map<String, String> properties) {
        List<String> required = REQUIRED_PROPERTIES.get(template);
        if (required == null) {
            throw new CustomException("No customer email template " + template, HttpStatus.BAD_REQUEST);
        }
        for (String name : required) {
            String value = properties == null ? null : properties.get(name);
            if (value == null || value.isBlank()) {
                throw new CustomException("properties." + name + " is required for " + template, HttpStatus.BAD_REQUEST);
            }
        }
    }

    public EmailDto render(UserEntity user, EmailTemplate template, Map<String, String> source) {
        validate(template, source);
        Map<String, String> properties = new LinkedHashMap<>(source);
        String username = user.getUsername();
        properties.put("username", username);
        String orderId = properties.get("orderId");
        String subject;
        String message;
        switch (template) {
            case ORDER_SHIPPED -> {
                subject = "Order #" + orderId + " has shipped";
                message = "Hi " + username + ",\n\nYour order #" + orderId + " is on its way with "
                        + properties.get("carrier") + ". Tracking number: " + properties.get("trackingNumber") + ".";
            }
            case ORDER_DELIVERED -> {
                subject = "Order #" + orderId + " was delivered";
                message = "Hi " + username + ",\n\nYour order #" + orderId
                        + " was delivered. You can now review the products and request a return within 30 days.";
            }
            case RETURN_UPDATED -> {
                String returnId = properties.get("returnId");
                String status = properties.get("status");
                String refund = blank(properties.get("refundAmount")) ? "" : " Refund: " + properties.get("refundAmount") + ".";
                String note = blank(properties.get("note")) ? "" : " Note: " + properties.get("note");
                subject = "Return #" + returnId + " is " + status;
                message = "Hi " + username + ",\n\nYour return #" + returnId + " for order #" + orderId
                        + " is now " + status + "." + refund + note;
            }
            case BACK_IN_STOCK -> {
                String productName = properties.get("productName");
                subject = productName + " is back in stock";
                message = "Hi " + username + ",\n\n" + productName
                        + " from your wishlist is available again. Order soon, stock is limited.";
            }
            default -> throw new CustomException("No customer email template " + template, HttpStatus.BAD_REQUEST);
        }
        return EmailDto.builder()
                .to(user.getEmail())
                .subject(subject)
                .message(message)
                .template(template)
                .properties(properties)
                .build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
