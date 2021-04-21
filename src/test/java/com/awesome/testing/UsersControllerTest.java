package com.awesome.testing;

import com.awesome.testing.dto.UserResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersControllerTest extends HttpHelper {

    @Test
    public void shouldGetUser() {
        // when
        ResponseEntity<UserResponseDTO> userResponseEntity = executeGet("/users/admin", UserResponseDTO.class);

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(userResponseEntity.getBody());
    }

    @Test
    public void shouldGetUnauthorized() {
        // when
        ResponseEntity<Object> userResponseEntity = restTemplate.exchange(
                "/users/admin",
                HttpMethod.GET,
                new HttpEntity<>(getUnauthorizedHeaders()),
                Object.class);

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
