package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class GetSingleUserControllerTest extends DomainHelper {

    @Test
    void shouldGetUserAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(user);

        // when
        ResponseEntity<UserResponseDto> response =
                executeGet(getUserEndpoint(user.getUsername()),
                        getHeadersWith(adminToken),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(getUserEndpoint("nonexisting"), getJsonOnlyHeaders(), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);

        // when
        ResponseEntity<ErrorDto> response =
                executeGet(getUserEndpoint("nonexisting"),
                        getHeadersWith(token),
                        ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }

}
