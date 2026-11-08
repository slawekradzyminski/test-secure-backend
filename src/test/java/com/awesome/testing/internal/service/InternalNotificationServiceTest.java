package com.awesome.testing.internal.service;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalNotificationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private InternalNotificationService service;

    @ParameterizedTest
    @EnumSource(value = EmailTemplate.class, names = {"ORDER_SHIPPED", "ORDER_DELIVERED", "RETURN_UPDATED"})
    void publishesTheRequestForAKnownCustomer(EmailTemplate template) {
        lenient().when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(new UserEntity()));

        service.notify(new InternalNotificationDto("buyer", template, "Subject", "Body", Map.of("orderId", "7")));

        verify(eventPublisher).publishEvent(new CustomerNotificationRequestedEvent(
                "buyer", template, "Subject", "Body", Map.of("orderId", "7")));
    }

    @Test
    void unknownCustomersAreNotFound() {
        assertThatThrownBy(() -> service.notify(
                new InternalNotificationDto("ghost", EmailTemplate.ORDER_SHIPPED, "s", "m", null)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @ParameterizedTest
    @EnumSource(value = EmailTemplate.class, names = {"GENERIC", "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_CONFIRMED", "BACK_IN_STOCK"})
    void templatesOutsideTheWarehouseSetAreRejectedBeforeTheLookup(EmailTemplate template) {
        assertThatThrownBy(() -> service.notify(new InternalNotificationDto("buyer", template, "s", "m", null)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userRepository, never()).findByUsername(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
