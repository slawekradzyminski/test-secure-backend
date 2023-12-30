package com.awesome.testing.swagger;

import com.awesome.testing.DomainHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class SwaggerConsoleTest extends DomainHelper {
    
    @Test
    public void shouldDisplaySwaggerConsole() {
        // when
        ResponseEntity<String> responseWithToken =
                executeGet("/swagger-ui/index.html", new HttpHeaders(), String.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

}
