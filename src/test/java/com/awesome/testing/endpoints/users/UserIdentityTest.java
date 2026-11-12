package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUser;
import static org.assertj.core.api.Assertions.assertThat;

/** Uniqueness and canonical form of usernames and emails (catalog T-007, T-008). */
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
}
