package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.LoginResponseDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class RefreshControllerTest extends DomainHelper {

    private static final String REFRESH_ENDPOINT = "/users/refresh";

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldRefreshTwice() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String apiToken = registerAndThenLoginSavingToken(user);

        // when
        String refreshedToken =
                executeGet(REFRESH_ENDPOINT, getHeadersWith(apiToken), LoginResponseDTO.class)
                        .getHeaders().get("Set-Cookie").get(0);

        ResponseEntity<LoginResponseDTO> response = executeGet(REFRESH_ENDPOINT,
                getHeadersWith(getTokenValueFromCookie(refreshedToken)), LoginResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(LoginResponseDTO.class);
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
