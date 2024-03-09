package com.awesome.testing.endpoints.email;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.users.ErrorDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.jms.DelayProvider;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class EmailControllerTest extends DomainHelper {

    private static final String EMAIL_ENDPOINT = "/email";

    @Value("${activemq.destination}")
    private String destination;

    @MockBean
    private JmsTemplate jmsTemplate;

    @MockBean
    private DelayProvider delayProvider;

    @Test
    public void shouldSentEmail() {
        // given
        when(delayProvider.getRandomDelayInSeconds()).thenReturn(1L);
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        EmailDto emailDTO = new EmailDto("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<Void> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getHeadersWith(token), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jmsTemplate, timeout(2000)).convertAndSend(destination, emailDTO);
    }

    @SneakyThrows
    @Test
    public void shouldReturn200AndDoNotSendEmail() {
        // given
        when(delayProvider.getRandomDelayInSeconds()).thenReturn(60L);
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        EmailDto emailDTO = new EmailDto("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<Void> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getHeadersWith(token), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Thread.sleep(1000);
        verify(jmsTemplate, never()).convertAndSend(destination, emailDTO);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        EmailDto emailDTO = new EmailDto("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<ErrorDto> response =
                executePost(EMAIL_ENDPOINT, emailDTO, getJsonOnlyHeaders(), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
