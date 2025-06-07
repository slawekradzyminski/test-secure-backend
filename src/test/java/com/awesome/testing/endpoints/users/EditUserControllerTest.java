package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.*;
import com.awesome.testing.dto.user.*;
import com.awesome.testing.dto.user.Role;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.factory.UserFactory.getRandomEmail;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class EditUserControllerTest extends DomainHelper {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateUserAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String token = getToken(user);
        UserEditDto userEditDto = getRandomUserEditBody();

        // when
        ResponseEntity<UserResponseDto> response = executePut(
                getUserEndpoint(username),
                userEditDto,
                getHeadersWith(token),
                UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDto loginResponse =
                attemptLogin(new LoginDto(user.getUsername(), user.getPassword()),
                        LoginResponseDto.class)
                        .getBody();

        assertThat(loginResponse.getLastName()).isEqualTo(userEditDto.getLastName());
        assertThat(loginResponse.getFirstName()).isEqualTo(userEditDto.getFirstName());
        assertThat(loginResponse.getEmail()).isEqualTo(userEditDto.getEmail());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldPartiallyUpdateUserAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String token = getToken(user);
        String newEmail = "newEmail@gmail.com";
        UserEditDto userEditDto = UserEditDto.builder()
                .email(newEmail)
                .build();

        // when
        ResponseEntity<UserResponseDto> response = executePut(
                getUserEndpoint(username),
                userEditDto,
                getHeadersWith(token),
                UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(username);
        assertThat(response.getBody().getEmail()).isEqualTo(newEmail);
    }

    @Test
    public void shouldGet200AsClientEditingHimself() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String clientToken = getToken(user);
        UserEditDto userEditDto = getRandomUserEditBody();

        // when
        ResponseEntity<UserResponseDto> response = executePut(
                getUserEndpoint(username),
                userEditDto,
                getHeadersWith(clientToken),
                UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet400IfInvalidBody() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String clientToken = getToken(user);
        UserEditDto userEditDto = UserEditDto.builder()
                .email("")
                .firstName("abcde")
                .lastName("abcde")
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                getUserEndpoint(username),
                userEditDto,
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        UserEditDto userEditDto = getRandomUserEditBody();

        // when
        ResponseEntity<ErrorDto> response = executePut(
                getUserEndpoint(username),
                userEditDto,
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGet403AsClientEditingSomeoneElse() {
        // given
        UserRegisterDto userToEdit = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(userToEdit);

        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(user);
        UserEditDto userEditDto = getRandomUserEditBody();

        // when
        ResponseEntity<UserResponseDto> response = executePut(
                getUserEndpoint(userToEdit.getUsername()),
                userEditDto,
                getHeadersWith(clientToken),
                UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String clientToken = getToken(user);
        UserEditDto userEditDto = getRandomUserEditBody();

        // when
        ResponseEntity<ErrorDto> response = executePut(
                getUserEndpoint("nonexisting"),
                userEditDto,
                getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UserEditDto getRandomUserEditBody() {
        return UserEditDto.builder()
                .email(getRandomEmail())
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }
}
