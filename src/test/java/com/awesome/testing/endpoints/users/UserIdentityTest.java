package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.IdentityIssue;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserIdentityIssueDto;
import com.awesome.testing.entity.UserEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Uniqueness, canonical form and enumeration of usernames and emails (catalog T-007, T-008, T-011). */
@SuppressWarnings("ConstantConditions")
class UserIdentityTest extends DomainHelper {

    private static final String LEGACY_PASSWORD = "legacy-password";
    private static final String IDENTITY_ISSUES = "/api/v1/admin/users/identity-issues";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void emailsAreUniqueRegardlessOfCaseAndStoredLowerCase() {
        // given
        UserRegisterDto first = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        first.setEmail("Mixed.Case" + System.nanoTime() + "@Example.COM");
        registerUser(first);
        UserRegisterDto second = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        second.setEmail(first.getEmail().toUpperCase(java.util.Locale.ROOT));

        // when
        ResponseEntity<ErrorDto> duplicate = executePost(REGISTER_ENDPOINT, second, getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody().getMessage()).isEqualTo("Email is already in use");
        assertThat(userRepository.findByUsername(first.getUsername()).orElseThrow().getEmail())
                .isEqualTo(first.getEmail().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void usernamesAreUniqueRegardlessOfCase() {
        // given
        UserRegisterDto first = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        first.setUsername("case" + System.nanoTime());
        registerUser(first);
        UserRegisterDto second = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        second.setUsername(first.getUsername().toUpperCase(java.util.Locale.ROOT));

        // when
        ResponseEntity<ErrorDto> duplicate = executePost(REGISTER_ENDPOINT, second, getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody().getMessage()).isEqualTo("Username is already in use");
    }

    @Test
    void newUsernamesUseLettersDigitsDotsUnderscoresAndHyphens() {
        for (String username : new String[] {"name\n", "trailing ", "with space", "a1@example.com", "tab\tname",
                "zażółć", "semi;colon"}) {
            // given
            UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
            user.setUsername(username);

            // when
            ResponseEntity<Map<String, String>> response = executePost(REGISTER_ENDPOINT, user, getJsonOnlyHeaders(),
                    mapTypeReference());

            // then
            assertThat(response.getStatusCode()).as(username).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(username).isEqualTo(Map.of("username",
                    "Username may only contain letters, digits, dots, underscores and hyphens"));
        }
    }

    @Test
    void generatorStyleUsernamesAreAccepted() {
        for (String username : new String[] {"user" + System.nanoTime(), "api-" + java.util.UUID.randomUUID(),
                "Jan.Kowalski_" + System.nanoTime()}) {
            // given
            UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
            user.setUsername(username);

            // when
            ResponseEntity<String> response = executePost(REGISTER_ENDPOINT, user, getJsonOnlyHeaders(), String.class);

            // then
            assertThat(response.getStatusCode()).as(username).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    void signInIgnoresTheCaseOfTheUsernameAndReturnsTheStoredOne() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        user.setUsername("Stored" + System.nanoTime());
        registerUser(user);

        // when
        ResponseEntity<LoginResponseDto> response = attemptLogin(
                new LoginDto(user.getUsername().toLowerCase(java.util.Locale.ROOT), user.getPassword()),
                LoginResponseDto.class);
        ResponseEntity<UserResponseDto> me = executeGet(USERS_ENDPOINT + "/me",
                getHeadersWith(response.getBody().getToken()), UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(user.getUsername());
        assertThat(me.getBody().getUsername()).isEqualTo(user.getUsername());
    }

    @Test
    void passwordResetForAnEmailThatIsAlsoAnotherUsersUsernameDoesNotFail() {
        // given: a legacy account whose username is the email of another account
        UserRegisterDto owner = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        owner.setEmail("owner" + System.nanoTime() + "@example.com");
        registerUser(owner);
        legacyUser(owner.getEmail(), "legacy" + System.nanoTime() + "@example.org");

        // when
        ResponseEntity<String> response = executePost("/api/v1/users/password/forgot",
                Map.of("identifier", owner.getEmail().toUpperCase(java.util.Locale.ROOT)), getJsonOnlyHeaders(),
                String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void legacyAccountsKeepSigningInAndAreListedForAdministrators() {
        // given
        String suffix = String.valueOf(System.nanoTime());
        UserEntity spaced = legacyUser("legacy user " + suffix, "Legacy" + suffix + "@Example.com");
        UserEntity upper = legacyUser("Twin" + suffix, "twin-a" + suffix + "@example.com");
        UserEntity lower = legacyUser("twin" + suffix, "twin-b" + suffix + "@example.com");
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
        String clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        ResponseEntity<String> spacedLogin = attemptLogin(new LoginDto(spaced.getUsername(), LEGACY_PASSWORD), String.class);
        ResponseEntity<String> upperLogin = attemptLogin(new LoginDto(upper.getUsername(), LEGACY_PASSWORD), String.class);
        ResponseEntity<String> lowerLogin = attemptLogin(new LoginDto(lower.getUsername(), LEGACY_PASSWORD), String.class);
        ResponseEntity<String> ambiguousLogin = attemptLogin(
                new LoginDto(upper.getUsername().toUpperCase(java.util.Locale.ROOT), LEGACY_PASSWORD), String.class);
        ResponseEntity<List<UserIdentityIssueDto>> report = executeGet(IDENTITY_ISSUES, getHeadersWith(adminToken),
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ErrorDto> asClient = executeGet(IDENTITY_ISSUES, getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> anonymous = executeGet(IDENTITY_ISSUES, getJsonOnlyHeaders(), ErrorDto.class);

        // then
        assertThat(spacedLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upperLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lowerLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ambiguousLogin.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getBody()).contains(
                new UserIdentityIssueDto(spaced.getId(), spaced.getUsername(), spaced.getEmail(),
                        List.of(IdentityIssue.USERNAME_FORMAT, IdentityIssue.EMAIL_NOT_CANONICAL)),
                new UserIdentityIssueDto(upper.getId(), upper.getUsername(), upper.getEmail(),
                        List.of(IdentityIssue.USERNAME_CASE_DUPLICATE)),
                new UserIdentityIssueDto(lower.getId(), lower.getUsername(), lower.getEmail(),
                        List.of(IdentityIssue.USERNAME_CASE_DUPLICATE)));
        assertThat(asClient.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changingTheEmailToACaseVariantOfAnotherUsersEmailIsRejected() {
        // given
        UserRegisterDto first = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(first);
        UserRegisterDto second = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(second);

        // when
        ResponseEntity<ErrorDto> response = executePut(getUserEndpoint(second.getUsername()),
                Map.of("email", first.getEmail().toUpperCase(java.util.Locale.ROOT), "firstName", "Changed",
                        "lastName", "Changed"),
                getHeadersWith(token), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Email is already in use");
    }

    private UserEntity legacyUser(String username, String email) {
        return userRepository.saveAndFlush(UserEntity.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(LEGACY_PASSWORD))
                .roles(List.of(Role.ROLE_CLIENT))
                .firstName("Legacy")
                .lastName("Account")
                .build());
    }
}
