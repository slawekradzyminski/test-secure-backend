package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.UserDataDTO;
import com.awesome.testing.dto.UserResponseDTO;
import com.awesome.testing.model.Role;
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
        UserDataDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        validUsername = user.getUsername();
        apiToken = registerUser(user).getBody();
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
