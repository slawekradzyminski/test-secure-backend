package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDTO;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.dto.users.UserResponseDTO;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetSingleUserControllerTest extends DomainHelper {

    @Test
    public void shouldGetUserAsAdmin() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(getUserEndpoint(user.getUsername()),
                        getHeadersWith(adminToken),
                        UserResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<ErrorDTO> userResponseEntity = restTemplate.exchange(
                getUserEndpoint(user.getUsername()),
                HttpMethod.GET,
                new HttpEntity<>(getJsonOnlyHeaders()),
                ErrorDTO.class);

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<ErrorDTO> response =
                executeGet(getUserEndpoint("nonexisting"),
                        getHeadersWith(token),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }

}
