package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.dto.UserResponseDto;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class MeControllerTest extends DomainHelper {

    private String validUsername;
    private String apiToken;

    private static final String ME_ENDPOINT = "/users/me";

    @BeforeEach
    public void prepareUserForTest() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        validUsername = user.getUsername();
        apiToken = getToken(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturnMyData() {
        // when
        ResponseEntity<UserResponseDto> response =
                executeGet(ME_ENDPOINT, getHeadersWith(apiToken), UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(validUsername);
        assertThat(response.getBody().getRoles()).containsExactlyInAnyOrder(Role.ROLE_CLIENT);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet401AsUnauthorized() {
        ResponseEntity<ErrorDto> response = executeGet("/users/me", getJsonOnlyHeaders(), ErrorDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

}
