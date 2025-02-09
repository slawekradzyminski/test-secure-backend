package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.dto.UserResponseDto;
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
    public void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(getUserEndpoint("nonexisting"), getJsonOnlyHeaders(), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForNonExistingUser() {
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
