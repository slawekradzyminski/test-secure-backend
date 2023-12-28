package com.awesome.testing.swagger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("dev")
@AutoConfigureMockMvc(addFilters = false)
public abstract class AbstractMockMvcTest {

    @Autowired
    protected MockMvc mockMvc;

}
