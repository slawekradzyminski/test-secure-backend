package com.awesome.testing.jms;

import com.awesome.testing.config.TestConfig;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.service.EmailService;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Producer half of the JMS email contract with jms-email-consumer.
 *
 * <p>The contract lives in {@code src/test/resources/contracts/email-jms-contract.json}, which must stay
 * byte-identical to the copy in jms-email-consumer. That repository publishes the same fixture through a real
 * Artemis broker and asserts that {@code EmailConsumer} sends the email, so a change to the destination, type
 * header or payload shape here fails this test until both copies of the fixture are updated together.
 */
@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class EmailJmsProducerContractTest {

    private static final Duration RECEIVE_DEADLINE = Duration.ofSeconds(10);

    @Autowired
    private EmailService emailService;

    @Autowired
    private ActiveMQConnectionFactory connectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${activemq.destination}")
    private String configuredDestination;

    @Test
    void shouldPublishEmailEventMatchingTheConsumerContract() throws Exception {
        JsonNode contract = readContract();
        String destination = contract.get("destination").asString();
        JsonNode expectedPayload = contract.get("payload");

        assertThat(configuredDestination)
                .as("activemq.destination used by the test profile")
                .isEqualTo(destination);
        assertThat(productionDestination())
                .as("activemq.destination in application-docker.yml (profile used by awesome-localstack)")
                .isEqualTo(destination);

        emailService.sendEmail(emailFrom(expectedPayload), configuredDestination);

        TextMessage message = receiveContractMessage(destination, expectedPayload.get("to").asString());
        assertThat(message.getStringProperty(contract.get("typeIdProperty").asString()))
                .as("JMS type header consumed by JacksonJsonMessageConverter in jms-email-consumer")
                .isEqualTo(contract.get("typeId").asString());
        assertThat(objectMapper.readTree(message.getText()))
                .as("serialized EmailDto payload")
                .isEqualTo(expectedPayload);
    }

    private EmailDto emailFrom(JsonNode payload) {
        Map<String, String> properties = new LinkedHashMap<>();
        payload.get("properties").properties()
                .forEach(entry -> properties.put(entry.getKey(), entry.getValue().asString()));
        return EmailDto.builder()
                .to(payload.get("to").asString())
                .subject(payload.get("subject").asString())
                .message(payload.get("message").asString())
                .template(EmailTemplate.valueOf(payload.get("template").asString()))
                .properties(properties)
                .build();
    }

    /**
     * Other test classes run concurrently and publish to the same embedded queue, so pick the message carrying
     * this contract's unique recipient. Matching on the raw text keeps the lookup independent of field names.
     */
    private TextMessage receiveContractMessage(String destination, String recipient) throws JMSException {
        JmsTemplate receiver = new JmsTemplate((ConnectionFactory) connectionFactory);
        receiver.setReceiveTimeout(500);
        Instant deadline = Instant.now().plus(RECEIVE_DEADLINE);
        while (Instant.now().isBefore(deadline)) {
            Message message = receiver.receive(destination);
            if (message instanceof TextMessage textMessage && textMessage.getText().contains(recipient)) {
                return textMessage;
            }
        }
        return fail("No email event for %s arrived on queue '%s' within %s", recipient, destination, RECEIVE_DEADLINE);
    }

    private JsonNode readContract() throws IOException {
        return objectMapper.readTree(new ClassPathResource("contracts/email-jms-contract.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String productionDestination() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-docker.yml"));
        Properties properties = yaml.getObject();
        return properties == null ? null : properties.getProperty("activemq.destination");
    }
}
