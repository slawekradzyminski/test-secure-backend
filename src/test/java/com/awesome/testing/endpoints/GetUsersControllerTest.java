package com.awesome.testing.endpoints;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

public class GetUsersControllerTest extends HttpHelper {

    @Test
    public void shouldGetUser() {
        // when
        ResponseEntity<UserResponseDTO> userResponseEntity =
                executeGet("/users/admin",
                getAdminHeaders(),
                UserResponseDTO.class);

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet403AsClient() {
        // when
        ResponseEntity<ErrorDTO> response =
                executeGet("/users/admin",
                        getClientHeaders(),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<Object> userResponseEntity = restTemplate.exchange(
                "/users/admin",
                HttpMethod.GET,
                new HttpEntity<>(getJsonOnlyHeaders()),
                Object.class);

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
