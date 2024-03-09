package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class MeControllerTest extends DomainHelper {

    private String validUsername;
    private String apiToken;

    private static final String ME_ENDPOINT = "/users/me";

    @BeforeEach
    public void prepareUserForTest() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        validUsername = user.getUsername();
        apiToken = registerAndThenLoginSavingToken(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturnMyData() {
        // when
        ResponseEntity<UserResponseDto> response =
                executeGet(ME_ENDPOINT, getHeadersWith(apiToken), UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(validUsername);
        assertThat(response.getBody().getRoles()).containsExactlyInAnyOrder(Role.ROLE_CLIENT);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<UserResponseDto> response =
                executeGet(ME_ENDPOINT, getJsonOnlyHeaders(), UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
