package com.awesome.testing.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Minimal JSON converter that avoids deprecated Spring JMS converter classes.
 */
public class JsonTextMessageConverter implements MessageConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Message toMessage(Object object, Session session) throws JMSException {
        TextMessage message = session.createTextMessage(serialize(object));
        message.setStringProperty("_awesome_", object.getClass().getName());
        return message;
    }

    @Override
    public Object fromMessage(Message message) throws JMSException {
        if (!(message instanceof TextMessage textMessage)) {
            throw new MessageConversionException("Only TextMessage is supported");
        }
        return textMessage.getText();
    }

    private String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException ex) {
            throw new MessageConversionException("Failed to serialize JMS payload", ex);
        }
    }
}
