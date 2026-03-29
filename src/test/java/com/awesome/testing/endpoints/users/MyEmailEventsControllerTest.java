package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailEventDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.delay.DelayGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.emailEventListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyEmailEventsControllerTest extends DomainHelper {

    private static final String FORGOT_PASSWORD_ENDPOINT = "/api/v1/users/password/forgot";
    private static final String MY_EMAIL_EVENTS_ENDPOINT = "/api/v1/users/me/email-events";

    @MockitoBean
    private JmsTemplate jmsTemplate;

    @MockitoBean
    private DelayGenerator delayGenerator;

    @org.springframework.beans.factory.annotation.Autowired
    private EmailEventRepository emailEventRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        emailEventRepository.deleteAll();
        when(delayGenerator.getDelayMillis()).thenReturn(0L);
    }

    @Test
    void shouldReturnOnlyCurrentUsersEmailEvents() {
        UserRegisterDto owner = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String ownerToken = getToken(owner);

        UserRegisterDto otherUser = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(otherUser);

        triggerForgotPassword(owner.getUsername());
        triggerForgotPassword(otherUser.getUsername());

        verify(jmsTemplate, timeout(500).times(2)).convertAndSend(anyString(), any(EmailDto.class));

        ResponseEntity<List<EmailEventDto>> response = executeGet(
                MY_EMAIL_EVENTS_ENDPOINT,
                getHeadersWith(ownerToken),
                emailEventListTypeReference()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        EmailEventDto event = response.getBody().getFirst();
        assertThat(event.getType()).isEqualTo(EmailTemplate.PASSWORD_RESET_REQUESTED);
        assertThat(event.getStatus()).isIn(EmailDeliveryStatus.QUEUED, EmailDeliveryStatus.SENT_TO_SMTP_SINK);
        assertThat(event.getRecipientMasked()).contains("***@");
        assertThat(event.getRecipientMasked()).doesNotContain(owner.getEmail());
    }

    @Test
    void shouldGet401WithoutAuthentication() {
        ResponseEntity<ErrorDto> response = executeGet(
                MY_EMAIL_EVENTS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    private void triggerForgotPassword(String username) {
        ResponseEntity<Void> response = executePost(
                FORGOT_PASSWORD_ENDPOINT,
                ForgotPasswordRequestDto.builder().identifier(username).build(),
                getJsonOnlyHeaders(),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
