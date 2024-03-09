package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.LoginResponseDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class RefreshControllerTest extends DomainHelper {

    private static final String REFRESH_ENDPOINT = "/users/refresh";

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldRefreshTwice() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String apiToken = registerAndThenLoginSavingToken(user);

        // when
        String refreshedToken =
                executeGet(REFRESH_ENDPOINT, getHeadersWith(apiToken), LoginResponseDto.class)
                        .getHeaders().get("Set-Cookie").get(0);

        ResponseEntity<LoginResponseDto> response = executeGet(REFRESH_ENDPOINT,
                getHeadersWith(getTokenValueFromCookie(refreshedToken)), LoginResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(LoginResponseDto.class);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<String> response =
                executeGet(REFRESH_ENDPOINT, getJsonOnlyHeaders(), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
