package com.awesome.testing.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseDefinitionTest {

    @Test
    void shouldWriteJsonErrorResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErrorResponseDefinition.sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Validation failed");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"message\": \"Validation failed\"");
    }
}
