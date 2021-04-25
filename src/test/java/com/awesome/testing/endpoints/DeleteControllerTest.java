package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserDataDTO;
import com.awesome.testing.model.Role;
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
        UserDataDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String apiToken = registerUser(user).getBody();

        // when
        ResponseEntity<String> response =
                executeDelete(getUserEndpoint(username), getHeadersWith(apiToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserDataDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();

        // when
        ResponseEntity<ErrorDTO> response =
                executeDelete(getUserEndpoint(username),
                        getJsonOnlyHeaders(),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserDataDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String apiToken = registerUser(user).getBody();

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
        UserDataDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = registerUser(user).getBody();

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
