package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Queues customer emails requested by warehouse-service through the existing email pipeline. */
@Service
@RequiredArgsConstructor
public class InternalNotificationService {

    static final Set<EmailTemplate> WAREHOUSE_TEMPLATES =
            Set.of(EmailTemplate.ORDER_SHIPPED, EmailTemplate.ORDER_DELIVERED, EmailTemplate.RETURN_UPDATED);

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void notify(InternalNotificationDto notification) {
        if (!WAREHOUSE_TEMPLATES.contains(notification.template())) {
            throw new CustomException(
                    "template must be ORDER_SHIPPED, ORDER_DELIVERED or RETURN_UPDATED", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.findByUsername(notification.username()).isEmpty()) {
            throw new CustomException("User not found", HttpStatus.NOT_FOUND);
        }
        eventPublisher.publishEvent(new CustomerNotificationRequestedEvent(
                notification.username(),
                notification.template(),
                notification.subject(),
                notification.message(),
                notification.properties()));
    }
}
