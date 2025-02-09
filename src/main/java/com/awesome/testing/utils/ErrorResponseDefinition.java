package com.awesome.testing.utils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

import java.io.IOException;

@UtilityClass
public class ErrorResponseDefinition {

    public static void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status.value());
        response.getWriter().write("""
            {
                "message": "%s"
            }
            """.formatted(message));
    }

}
