package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.*;
import com.awesome.testing.model.Role;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomEmail;
import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class EditUserControllerTest extends DomainHelper {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateUserAsAdmin() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String token = registerAndGetToken(user);
        UserEditDTO userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint(username),
                userEditDTO,
                getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDTO loginResponse =
                attemptLogin(new LoginDTO(user.getUsername(), user.getPassword()),
                        LoginResponseDTO.class)
                        .getBody();

        assertThat(loginResponse.getLastName()).isEqualTo(userEditDTO.getLastName());
        assertThat(loginResponse.getFirstName()).isEqualTo(userEditDTO.getFirstName());
        assertThat(loginResponse.getRoles()).isEqualTo(userEditDTO.getRoles());
        assertThat(loginResponse.getEmail()).isEqualTo(userEditDTO.getEmail());
    }

    @Test
    public void shouldGet400IfInvalidBody() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String clientToken = registerAndGetToken(user);
        UserEditDTO userEditDTO = UserEditDTO.builder()
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
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String clientToken = registerAndGetToken(user);
        UserEditDTO userEditDTO = getRandomUserEditBody();

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
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        UserEditDTO userEditDTO = getRandomUserEditBody();

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
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String clientToken = registerAndGetToken(user);
        UserEditDTO userEditDTO = getRandomUserEditBody();

        // when
        ResponseEntity<Object> response = executePut(getUserEndpoint("nonexisting"),
                userEditDTO,
                getHeadersWith(clientToken));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UserEditDTO getRandomUserEditBody() {
        return UserEditDTO.builder()
                .email(getRandomEmail())
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

}
