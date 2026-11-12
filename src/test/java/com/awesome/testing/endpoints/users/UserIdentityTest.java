package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUser;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** Uniqueness, canonical form and enumeration of usernames and emails (catalog T-007, T-008, T-011). */
@SuppressWarnings("ConstantConditions")
class UserIdentityTest extends DomainHelper {

    @Autowired
    private UserRepository userRepository;

    @Test
    void changingTheEmailToAnotherUsersEmailIsRejected() {
        // given
        UserRegisterDto first = getRandomUser();
        registerUser(first);
        UserRegisterDto second = getRandomUser();
        String token = getToken(second);

        // when
        ResponseEntity<ErrorDto> response = executePut(getUserEndpoint(second.getUsername()),
                Map.of("email", first.getEmail(), "firstName", "Changed", "lastName", "Changed"),
                getHeadersWith(token), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Email is already in use");
        assertThat(userRepository.findByUsername(second.getUsername()).orElseThrow().getFirstName())
                .isEqualTo(second.getFirstName());
    }

    @Test
    void keepingTheOwnEmailIsAllowed() {
        // given
        UserRegisterDto user = getRandomUser();
        String token = getToken(user);

        // when
        ResponseEntity<UserResponseDto> response = executePut(getUserEndpoint(user.getUsername()),
                Map.of("email", user.getEmail(), "firstName", "Changed", "lastName", "Changed"),
                getHeadersWith(token), UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFirstName()).isEqualTo("Changed");
    }

    @Test
    void aClientCannotTellAMissingUsernameFromAnotherUsersOnProfileEdit() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(client);
        UserRegisterDto other = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(other);
        Map<String, String> body = Map.of("email", "probe@example.com", "firstName", "Probe", "lastName", "Probe");

        // when
        ResponseEntity<ErrorDto> missing = executePut(getUserEndpoint("does-not-exist"), body,
                getHeadersWith(token), ErrorDto.class);
        ResponseEntity<ErrorDto> existing = executePut(getUserEndpoint(other.getUsername()), body,
                getHeadersWith(token), ErrorDto.class);

        // then
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missing.getBody().getMessage()).isEqualTo("Access denied");
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(existing.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void anAdministratorStillGets404ForAMissingUsername() {
        // given
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));

        // when
        ResponseEntity<ErrorDto> response = executePut(getUserEndpoint("does-not-exist"),
                Map.of("email", "probe@example.com", "firstName", "Probe", "lastName", "Probe"),
                getHeadersWith(adminToken), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }
}
