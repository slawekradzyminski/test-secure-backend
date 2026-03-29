package com.awesome.testing.endpoints;

import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.repository.EmailEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import com.awesome.testing.service.delay.DelayGenerator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.factory.EmailFactory.getRandomEmail;

class EmailControllerTest extends AbstractEcommerceTest {

    private static final String EMAIL_ENDPOINT = "/api/v1/email";

    @Value("${activemq.destination}")
    private String destination;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @MockitoBean
    private JmsTemplate jmsTemplate;

    @MockitoBean
    private DelayGenerator delayGenerator;

    @Test
    void shouldSendEmail() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String authToken = getToken(user);
        EmailDto emailDto = getRandomEmail();
        when(delayGenerator.getDelayMillis()).thenReturn(0L);

        // when
        ResponseEntity<Void> response = executePost(
                EMAIL_ENDPOINT,
                emailDto,
                getHeadersWith(authToken),
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emailEventRepository.findAll()).hasSize(1);
        assertThat(emailEventRepository.findAll().getFirst().getRecipientEmail()).isEqualTo(emailDto.getTo());
        assertThat(emailEventRepository.findAll().getFirst().getType()).isEqualTo(EmailTemplate.GENERIC);
        assertThat(emailEventRepository.findAll().getFirst().getStatus())
                .isIn(EmailDeliveryStatus.QUEUED, EmailDeliveryStatus.SENT_TO_SMTP_SINK);
        verify(jmsTemplate, timeout(500)).convertAndSend(destination, emailDto);
    }

    @Test
    void shouldGet400WhenEmailIsInvalid() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String authToken = getToken(user);
        EmailDto invalidEmail = EmailDto.builder()
                .to("invalid-email")  // invalid email format
                .subject("")          // empty subject
                .message("message")
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                EMAIL_ENDPOINT,
                invalidEmail,
                getHeadersWith(authToken),
                mapTypeReference()
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("to")).isEqualTo("Invalid email format");
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeader() {
        // given
        EmailDto emailDto = getRandomEmail();

        // when
        ResponseEntity<Void> response = executePost(
                EMAIL_ENDPOINT,
                emailDto,
                getJsonOnlyHeaders(),
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
