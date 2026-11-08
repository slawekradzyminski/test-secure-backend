package com.awesome.testing.jms;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.password.LocalEmailOutbox;
import com.awesome.testing.service.password.LocalEmailOutboxAvailability;
import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailTransportTest {

    private final EmailConfig config = new EmailConfig();

    @Test
    void localOutboxTransportStoresEmailsWithoutTouchingTheBroker() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        LocalEmailOutbox outbox = new LocalEmailOutbox();
        var jmsTemplate = template(connectionFactory, outbox, outboxEnabled(true), "local-outbox");
        EmailDto email = EmailDto.builder().to("user@example.test").subject("Hi").message("Body").build();

        jmsTemplate.convertAndSend("email", email);

        assertThat(outbox.getAll()).singleElement()
                .satisfies(stored -> assertThat(stored.getPayload()).isEqualTo(email));
        verifyNoInteractions(connectionFactory);
    }

    @Test
    void localOutboxTransportDropsEmailsWhenTheOutboxIsDisabled() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        LocalEmailOutbox outbox = new LocalEmailOutbox();
        var jmsTemplate = template(connectionFactory, outbox, outboxEnabled(false), "local-outbox");

        jmsTemplate.convertAndSend("email", EmailDto.builder().to("user@example.test").build());

        assertThat(outbox.getAll()).isEmpty();
        verifyNoInteractions(connectionFactory);
    }

    @Test
    void jmsTransportSendsThroughTheBroker() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        LocalEmailOutbox outbox = new LocalEmailOutbox();
        var jmsTemplate = template(connectionFactory, outbox, outboxEnabled(true), "jms");

        // The mock broker has no connection, so the send fails after reaching the connection factory.
        assertThatThrownBy(() -> jmsTemplate.convertAndSend("email", EmailDto.builder().to("user@example.test").build()))
                .isInstanceOf(RuntimeException.class);
        verify(connectionFactory).createConnection();
        assertThat(outbox.getAll()).isEmpty();
    }

    @Test
    void transportValuesAreValidated() {
        assertThat(EmailTransport.from(" JMS ")).isEqualTo(EmailTransport.JMS);
        assertThat(EmailTransport.from("local-outbox")).isEqualTo(EmailTransport.LOCAL_OUTBOX);
        assertThatThrownBy(() -> EmailTransport.from("smtp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.email.transport");
    }

    @Test
    void outboxIsAvailableOnlyForTheLocalOutboxTransportWhenEnabled() {
        assertThat(new LocalEmailOutboxAvailability(outboxEnabled(true), "local-outbox").isActive()).isTrue();
        assertThat(new LocalEmailOutboxAvailability(outboxEnabled(false), "local-outbox").isActive()).isFalse();
        assertThat(new LocalEmailOutboxAvailability(outboxEnabled(true), "jms").isActive()).isFalse();
    }

    private EmailJmsTemplate template(ConnectionFactory connectionFactory, LocalEmailOutbox outbox,
                                      PasswordResetProperties properties, String transport) {
        return (EmailJmsTemplate) config.jmsTemplate(connectionFactory,
                config.jacksonJmsMessageConverter(new ObjectMapper()), outbox, properties, transport);
    }

    private static PasswordResetProperties outboxEnabled(boolean enabled) {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setLocalOutboxEnabled(enabled);
        return properties;
    }
}
