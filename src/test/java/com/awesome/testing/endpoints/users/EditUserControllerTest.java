package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.*;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomEmail;
import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class EditUserControllerTest extends DomainHelper {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateYourselfAsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String token = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(username),
                userEditDTO,
                getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDto loginResponse =
                attemptLogin(new LoginDto(user.getUsername(), user.getPassword()),
                        LoginResponseDto.class)
                        .getBody();

        assertThat(loginResponse.getLastName()).isEqualTo(userEditDTO.getLastName());
        assertThat(loginResponse.getFirstName()).isEqualTo(userEditDTO.getFirstName());
        assertThat(loginResponse.getRoles()).isEqualTo(userEditDTO.getRoles());
        assertThat(loginResponse.getEmail()).isEqualTo(userEditDTO.getEmail());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateAnyUserAsAdmin() {
        // given
        UserRegisterDto userToEdit = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        register(userToEdit);
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(userToEdit.getUsername()),
                userEditDTO,
                getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDto loginResponse =
                attemptLogin(new LoginDto(userToEdit.getUsername(), userToEdit.getPassword()),
                        LoginResponseDto.class)
                        .getBody();

        assertThat(loginResponse.getLastName()).isEqualTo(userEditDTO.getLastName());
        assertThat(loginResponse.getFirstName()).isEqualTo(userEditDTO.getFirstName());
        assertThat(loginResponse.getRoles()).isEqualTo(userEditDTO.getRoles());
        assertThat(loginResponse.getEmail()).isEqualTo(userEditDTO.getEmail());
    }

    @Test
    public void shouldGet403WhenUpdatingAnyUserAsNotAdmin() {
        // given
        UserRegisterDto userToEdit = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        register(userToEdit);
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(userToEdit.getUsername()),
                userEditDTO,
                getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet400IfInvalidBody() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String clientToken = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = UserEditDto.builder()
                .email("")
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName("abcde")
                .lastName("abcde")
                .build();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(username),
                userEditDTO,
                getHeadersWith(clientToken));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldGet200AsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String clientToken = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(username),
                userEditDTO,
                getHeadersWith(clientToken));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(username),
                userEditDTO,
                getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String clientToken = registerAndThenLoginSavingToken(user);
        UserEditDto userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint("nonexisting"),
                userEditDTO,
                getHeadersWith(clientToken));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UserEditDto getRandomUserEditBody() {
        return UserEditDto.builder()
                .email(getRandomEmail())
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

}
