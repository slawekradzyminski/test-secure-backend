package com.awesome.testing.config;

import com.awesome.testing.fakedata.SetupData;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestConfiguration
public class TestConfig {
    
    @MockitoBean
    private SetupData setupData;
} 