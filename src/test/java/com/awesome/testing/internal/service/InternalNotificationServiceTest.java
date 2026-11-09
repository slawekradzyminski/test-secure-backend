package com.awesome.testing.internal.service;

import static org.mockito.Mockito.when;

import com.awesome.testing.service.notification.CustomerEmailRenderer;

import com.awesome.testing.service.email.EmailEventService;

import org.mockito.Spy;

import java.util.UUID;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalNotificationServiceTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Mock private UserRepository userRepository;
    @Mock private EmailEventService emailEventService;
    @Spy private CustomerEmailRenderer renderer = new CustomerEmailRenderer();
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private InternalNotificationService service;

    @ParameterizedTest
    @EnumSource(value = EmailTemplate.class, names = {"ORDER_SHIPPED", "ORDER_DELIVERED", "RETURN_UPDATED"})
    void publishesTheRequestForAKnownCustomer(EmailTemplate template) {
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(new UserEntity()));
        Map<String, String> properties = complete();

        service.notify(new InternalNotificationDto(REQUEST, "buyer", template, properties));

        verify(eventPublisher).publishEvent(new CustomerNotificationRequestedEvent(REQUEST, "buyer", template, properties));
    }

    @Test
    void aRepeatedRequestIdIsAcceptedWithoutQueueingTheEmailAgain() {
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(new UserEntity()));
        when(emailEventService.isRecorded(REQUEST)).thenReturn(true);

        service.notify(new InternalNotificationDto(REQUEST, "buyer", EmailTemplate.ORDER_DELIVERED, complete()));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void missingTemplatePropertiesAreRejectedBeforeTheLookup() {
        assertThatThrownBy(() -> service.notify(new InternalNotificationDto(
                REQUEST, "buyer", EmailTemplate.ORDER_SHIPPED, Map.of("orderId", "7", "carrier", "DPD"))))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("properties.trackingNumber is required for ORDER_SHIPPED");
                });
        verify(userRepository, never()).findByUsername(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownCustomersAreNotFound() {
        assertThatThrownBy(() -> service.notify(
                new InternalNotificationDto(REQUEST, "ghost", EmailTemplate.ORDER_DELIVERED, complete())))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @ParameterizedTest
    @EnumSource(value = EmailTemplate.class, names = {"GENERIC", "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_CONFIRMED", "BACK_IN_STOCK"})
    void templatesOutsideTheWarehouseSetAreRejectedBeforeTheLookup(EmailTemplate template) {
        assertThatThrownBy(() -> service.notify(new InternalNotificationDto(REQUEST, "buyer", template, null)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userRepository, never()).findByUsername(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static Map<String, String> complete() {
        return Map.of("orderId", "7", "carrier", "DPD", "trackingNumber", "12345678901234",
                "returnId", "3", "status", "APPROVED");
    }
}
