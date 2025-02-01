package com.awesome.testing.config;

import com.awesome.testing.fakedata.SetupData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jms.core.JmsTemplate;

@TestConfiguration
public class TestConfig {
    private static final Logger log = LoggerFactory.getLogger(TestConfig.class);

    @MockBean
    private SetupData setupData;

    @MockBean
    private JmsTemplate jmsTemplate;
} 