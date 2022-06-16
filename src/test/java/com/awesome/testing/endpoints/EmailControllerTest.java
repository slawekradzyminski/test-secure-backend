package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.EmailDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;

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
        EmailDTO emailDTO = new EmailDTO("slawek@gmail.com", "Important", "Read carefully");

        // when
        ResponseEntity<Void> response =
                executePost(EMAIL_ENDPOINT, emailDTO,getJsonOnlyHeaders(), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jmsTemplate, timeout(500)).convertAndSend(destination, emailDTO);
    }

}
