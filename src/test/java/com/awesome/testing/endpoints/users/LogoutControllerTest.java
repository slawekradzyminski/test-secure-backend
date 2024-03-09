package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class LogoutControllerTest extends DomainHelper {

    private static final String LOGOUT_ENDPOINT = "/users/logout";

    @Test
    public void shouldHandleUserWithoutCookie() {
        // when
        ResponseEntity<Void> logoutResponse =
                executePost(LOGOUT_ENDPOINT, "", getJsonOnlyHeaders(), Void.class);

        // then
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoutResponse.getHeaders().get("Set-Cookie").getFirst()).contains("token=");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldLogoutUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<Void> logoutResponse =
                executePost(LOGOUT_ENDPOINT, "", getHeadersWith(token), Void.class);

        // then
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoutResponse.getHeaders().get("Set-Cookie").getFirst()).contains("token=");
    }

    @Test
    public void shouldInvalidateTokenServerSide() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        ResponseEntity<UserResponseDto> response =
                executeGet(getUserEndpoint(user.getUsername()), getHeadersWith(token), UserResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // when
        executePost(LOGOUT_ENDPOINT, "", getHeadersWith(token), Void.class);

        // and then
        ResponseEntity<UserResponseDto> responseWithInvalidatedToken =
                executeGet(getUserEndpoint(user.getUsername()), getHeadersWith(token), UserResponseDto.class);

        // then
        assertThat(responseWithInvalidatedToken.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}