package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteControllerTest extends DomainHelper {

    @Test
    public void shouldDeleteUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String apiToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<?> response =
                executeDelete(getUserEndpoint(username), getHeadersWith(apiToken));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();

        // when
        ResponseEntity<?> response =
                executeDelete(getUserEndpoint(username),
                        getJsonOnlyHeaders(),
                        ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String apiToken = registerAndThenLoginSavingToken(user);

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
    public void shouldGet404Nonexisting() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = registerAndThenLoginSavingToken(user);

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
