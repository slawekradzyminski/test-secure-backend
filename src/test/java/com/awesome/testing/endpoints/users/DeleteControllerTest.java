package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDTO;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteControllerTest extends DomainHelper {

    @Test
    public void shouldDeleteUser() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
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
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();

        // when
        ResponseEntity<?> response =
                executeDelete(getUserEndpoint(username),
                        getJsonOnlyHeaders(),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String apiToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<ErrorDTO> response =
                executeDelete(getUserEndpoint(username),
                        getHeadersWith(apiToken),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404Nonexisting() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<ErrorDTO> response =
                executeDelete(getUserEndpoint("nonexisting"),
                        getHeadersWith(apiToken),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }

}
