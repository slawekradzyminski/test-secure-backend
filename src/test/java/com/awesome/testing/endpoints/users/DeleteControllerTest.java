package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class DeleteControllerTest extends DomainHelper {

    @Test
    public void shouldDeleteUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String apiToken = getToken(user);

        // when
        ResponseEntity<String> response =
                executeDelete(getUserEndpoint(username), getHeadersWith(apiToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeDelete(
                getUserEndpoint("nonexisting"),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String apiToken = getToken(user);

        // when
        ResponseEntity<ErrorDto> response =
                executeDelete(getUserEndpoint(username),
                        getHeadersWith(apiToken),
                        ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404IfUserNotFound() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = getToken(user);

        // when
        ResponseEntity<ErrorDto> response =
                executeDelete(getUserEndpoint("nonexisting"),
                        getHeadersWith(apiToken),
                        ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }

}
