package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.dto.users.UserResponseDTO;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class MeControllerTest extends DomainHelper {

    private String validUsername;
    private String apiToken;

    private static final String ME_ENDPOINT = "/users/me";

    @BeforeEach
    public void prepareUserForTest() {
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        validUsername = user.getUsername();
        apiToken = registerAndThenLoginSavingToken(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturnMyData() {
        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(ME_ENDPOINT, getHeadersWith(apiToken), UserResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(validUsername);
        assertThat(response.getBody().getRoles()).containsExactlyInAnyOrder(Role.ROLE_CLIENT);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(ME_ENDPOINT, getJsonOnlyHeaders(), UserResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
