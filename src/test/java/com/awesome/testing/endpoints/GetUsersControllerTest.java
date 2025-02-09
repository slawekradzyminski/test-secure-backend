package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.dto.UserResponseDto;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.userListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class GetUsersControllerTest extends DomainHelper {

    @Test
    public void shouldGetUsersAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(user);

        // when
        ResponseEntity<List<UserResponseDto>> response =
                executeGet(USERS_ENDPOINT,
                        getHeadersWith(adminToken),
                        userListTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                USERS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }
}
