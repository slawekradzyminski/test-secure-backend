package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class LogoutControllerTest extends DomainHelper {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldLoginUser() {
        // when
        ResponseEntity<Void> logoutResponse = executePost("/users/logout", "", getJsonOnlyHeaders(), Void.class);

        // then
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoutResponse.getHeaders().get("Set-Cookie").get(0)).contains("token=");
    }
}