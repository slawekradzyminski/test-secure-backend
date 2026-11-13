package com.awesome.testing.endpoints.users;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.UserResponseDto;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Catalog X-12: every client could list every account with its email address. The directory stays readable
 * by clients (the course asserts string emails for every user), but other accounts' addresses are masked.
 */
class UserDirectoryEmailMaskingTest extends DomainHelper {

    @Test
    void clientsSeeTheirOwnEmailAndOtherAccountsMasked() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto other = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(client);
        registerUser(other);

        // when
        var list = executeGet(USERS_ENDPOINT, getHeadersWith(token),
                new ParameterizedTypeReference<List<UserResponseDto>>() {});
        var single = executeGet(getUserEndpoint(other.getUsername()), getHeadersWith(token), UserResponseDto.class);
        var own = executeGet(getUserEndpoint(client.getUsername()), getHeadersWith(token), UserResponseDto.class);

        // then
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(email(list.getBody(), client.getUsername())).isEqualTo(client.getEmail().toLowerCase());
        assertThat(email(list.getBody(), other.getUsername())).isEqualTo(masked(other.getEmail()));
        assertThat(list.getBody()).filteredOn(user -> !user.getUsername().equals(client.getUsername()))
                .allSatisfy(user -> assertThat(user.getEmail()).matches(".\\*\\*\\*@.+"));
        assertThat(single.getBody().getEmail()).isEqualTo(masked(other.getEmail()));
        assertThat(single.getBody().getFirstName()).isEqualTo(other.getFirstName());
        assertThat(own.getBody().getEmail()).isEqualTo(client.getEmail().toLowerCase());
    }

    @Test
    void administratorsSeeEveryEmail() {
        // given
        UserRegisterDto other = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(other);
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));

        // when
        var list = executeGet(USERS_ENDPOINT, getHeadersWith(adminToken),
                new ParameterizedTypeReference<List<UserResponseDto>>() {});
        var single = executeGet(getUserEndpoint(other.getUsername()), getHeadersWith(adminToken),
                UserResponseDto.class);

        // then
        assertThat(email(list.getBody(), other.getUsername())).isEqualTo(other.getEmail().toLowerCase());
        assertThat(single.getBody().getEmail()).isEqualTo(other.getEmail().toLowerCase());
        assertThat(list.getBody()).noneSatisfy(user -> assertThat(user.getEmail()).contains("***"));
    }

    private static String email(List<UserResponseDto> users, String username) {
        return users.stream().filter(user -> user.getUsername().equals(username)).findFirst().orElseThrow().getEmail();
    }

    private static String masked(String email) {
        String canonical = email.toLowerCase();
        return canonical.charAt(0) + "***" + canonical.substring(canonical.lastIndexOf('@'));
    }
}
