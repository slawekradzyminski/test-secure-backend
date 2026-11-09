package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.email.EmailEventService;
import com.awesome.testing.service.notification.CustomerEmailRenderer;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Queues customer emails requested by warehouse-service through the existing email pipeline.
 * warehouse-service retries until it gets 202, so a repeated requestId is a no-op.
 */
@Service
@RequiredArgsConstructor
public class InternalNotificationService {

    static final Set<EmailTemplate> WAREHOUSE_TEMPLATES =
            Set.of(EmailTemplate.ORDER_SHIPPED, EmailTemplate.ORDER_DELIVERED, EmailTemplate.RETURN_UPDATED);

    private final UserRepository userRepository;
    private final EmailEventService emailEventService;
    private final CustomerEmailRenderer renderer;
    private final ApplicationEventPublisher eventPublisher;

    public void notify(InternalNotificationDto notification) {
        if (!WAREHOUSE_TEMPLATES.contains(notification.template())) {
            throw new CustomException(
                    "template must be ORDER_SHIPPED, ORDER_DELIVERED or RETURN_UPDATED", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> properties = notification.properties() == null ? Map.of() : notification.properties();
        renderer.validate(notification.template(), properties);
        if (userRepository.findByUsername(notification.username()).isEmpty()) {
            throw new CustomException("User not found", HttpStatus.NOT_FOUND);
        }
        if (emailEventService.isRecorded(notification.requestId())) {
            return;
        }
        eventPublisher.publishEvent(new CustomerNotificationRequestedEvent(
                notification.requestId(), notification.username(), notification.template(), properties));
    }
}
