package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class EmailControllerTest extends DomainHelper {

    private static final String EMAIL_ENDPOINT = "/email";

    @Value("${activemq.destination}")
    private String destination;

    @MockBean
    private JmsTemplate jmsTemplate;

    @Test
    public void shouldSentEmail() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndGetToken(user);
        EmailDTO emailDTO = new EmailDTO("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<Void> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getHeadersWith(token), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jmsTemplate, timeout(500)).convertAndSend(destination, emailDTO);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        EmailDTO emailDTO = new EmailDTO("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<ErrorDTO> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getJsonOnlyHeaders(), ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = registerAndGetToken(user);
        EmailDTO emailDTO = new EmailDTO("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<ErrorDTO> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getHeadersWith(token), ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
