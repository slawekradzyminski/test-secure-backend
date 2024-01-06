package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetUsersControllerTest extends DomainHelper {

    @Test
    public void shouldGetUsersAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<UserResponseDto[]> response =
                executeGet(USERS_ENDPOINT,
                        getHeadersWith(adminToken),
                        UserResponseDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> userResponseEntity = executeGet(
                USERS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(userResponseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
