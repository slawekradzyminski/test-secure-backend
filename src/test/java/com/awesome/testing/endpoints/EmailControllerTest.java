package com.awesome.testing.endpoints;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.factory.EmailFactory.getRandomEmail;

class EmailControllerTest extends AbstractEcommerceTest {

    private static final String EMAIL_ENDPOINT = "/email";

    @Value("${activemq.destination}")
    private String destination;

    @MockitoBean
    private JmsTemplate jmsTemplate;

    @Test
    void shouldSendEmail() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String authToken = getToken(user);
        EmailDTO emailDTO = getRandomEmail();

        // when
        ResponseEntity<Void> response = executePost(
                EMAIL_ENDPOINT,
                emailDTO,
                getHeadersWith(authToken),
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jmsTemplate).convertAndSend(destination, emailDTO);
    }

    @Test
    void shouldGet400WhenEmailIsInvalid() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String authToken = getToken(user);
        EmailDTO invalidEmail = EmailDTO.builder()
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
        EmailDTO emailDTO = getRandomEmail();

        // when
        ResponseEntity<Void> response = executePost(
                EMAIL_ENDPOINT,
                emailDTO,
                getJsonOnlyHeaders(),
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
