package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDTO;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.dto.users.UserResponseDTO;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetUsersControllerTest extends DomainHelper {

    @Test
    public void shouldGetUsersAsAdmin() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<UserResponseDTO[]> response =
                executeGet(USERS_ENDPOINT,
                        getHeadersWith(adminToken),
                        UserResponseDTO[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<ErrorDTO> userResponseEntity = executeGet(
                USERS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDTO.class
        );

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
