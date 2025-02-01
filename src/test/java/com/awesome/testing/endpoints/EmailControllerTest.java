package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.model.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;

public class EmailControllerTest extends DomainHelper {

    private static final String EMAIL_ENDPOINT = "/email";

    @Value("${activemq.destination}")
    private String destination;

    @Autowired
    private JmsTemplate jmsTemplate;

    private String authToken;

    @BeforeEach
    public void setup() {
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        authToken = getToken(user);
    }

    @Test
    public void shouldSentEmail() {
        // given
        EmailDTO emailDTO = EmailDTO.builder()
                .to("slawek@gmail.com")
                .subject("Important")
                .message("Read carefully")
                .build();

        // when
        ResponseEntity<Void> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getHeadersWith(authToken), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jmsTemplate, timeout(500)).convertAndSend(destination, emailDTO);
    }
}
