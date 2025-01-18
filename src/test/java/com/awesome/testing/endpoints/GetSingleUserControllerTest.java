package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.dto.UserResponseDTO;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class GetSingleUserControllerTest extends DomainHelper {

    @Test
    public void shouldGetUserAsAdmin() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(user);

        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(getUserEndpoint(user.getUsername()),
                        getHeadersWith(adminToken),
                        UserResponseDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        ResponseEntity<ErrorDTO> response = executeGet(getUserEndpoint("nonexisting"), getJsonOnlyHeaders(), ErrorDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);

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
