package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
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

    @Test
    public void shouldRefreshTwice() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String apiToken = registerUser(user).getBody();

        // when
        String refreshedToken =
                executeGet(REFRESH_ENDPOINT, getHeadersWith(apiToken), String.class)
                .getBody();

        ResponseEntity<String> response =
                executeGet(REFRESH_ENDPOINT, getHeadersWith(refreshedToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
