package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.dto.UserResponseDTO;
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
        String adminToken = registerAndGetToken(user);

        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(getUserEndpoint(user.getUsername()),
                        getHeadersWith(adminToken),
                        UserResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = registerAndGetToken(user);

        // when
        ResponseEntity<ErrorDTO> response =
                executeGet(getUserEndpoint(user.getUsername()),
                        getHeadersWith(clientToken),
                        ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerAndGetToken(user);

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
        String token = registerAndGetToken(user);

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
