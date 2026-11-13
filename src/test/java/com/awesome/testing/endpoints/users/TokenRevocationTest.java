package com.awesome.testing.endpoints.users;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.RefreshTokenRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.TokenRefreshResponseDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.token.PasswordResetTokenGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

/**
 * Catalog X-08: an access token stayed valid for its whole lifetime after logout and after a password
 * reset. Both now revoke every access token and every refresh token of the account.
 */
class TokenRevocationTest extends DomainHelper {

    private static final String ME = "/api/v1/users/me";
    private static final String REFRESH = "/api/v1/users/refresh";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordResetTokenGenerator passwordResetTokenGenerator;

    @Test
    void logoutRevokesTheAccessTokensOfEverySessionAndTheirRefreshTokens() {
        // given: the same user signed in on two devices
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto laptop = registerAndLogin(user);
        LoginResponseDto phone = signIn(user.getUsername(), user.getPassword());

        // when
        var logout = executePost("/api/v1/users/logout", null, getHeadersWith(laptop.getToken()), String.class);

        // then
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRevoked(laptop);
        assertRevoked(phone);
        LoginResponseDto again = signIn(user.getUsername(), user.getPassword());
        assertThat(executeGet(ME, getHeadersWith(again.getToken()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void passwordResetRevokesAccessAndRefreshTokens() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto session = registerAndLogin(user);
        String resetToken = resetTokenFor(user.getUsername());
        String newPassword = "UpdatedPassword123!";

        // when
        ResponseEntity<Void> reset = executePost("/api/v1/users/password/reset",
                new ResetPasswordRequestDto(resetToken, newPassword, newPassword), getJsonOnlyHeaders(), Void.class);

        // then
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRevoked(session);
        LoginResponseDto fresh = signIn(user.getUsername(), newPassword);
        assertThat(executeGet(ME, getHeadersWith(fresh.getToken()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aTokenRefreshedAfterTheRevocationIsValid() {
        // given: tokens refreshed on another device are bound to the current version
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto first = registerAndLogin(user);
        executePost("/api/v1/users/logout", null, getHeadersWith(first.getToken()), String.class);
        LoginResponseDto second = signIn(user.getUsername(), user.getPassword());

        // when
        var refreshed = executePost(REFRESH, new RefreshTokenRequestDto(second.getRefreshToken()),
                getJsonOnlyHeaders(), TokenRefreshResponseDto.class);

        // then
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(executeGet(ME, getHeadersWith(refreshed.getBody().getToken()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void assertRevoked(LoginResponseDto session) {
        ResponseEntity<ErrorDto> me = executeGet(ME, getHeadersWith(session.getToken()), ErrorDto.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me.getBody().getMessage()).isEqualTo("Invalid or expired token");
        ResponseEntity<ErrorDto> refresh = executePost(REFRESH,
                new RefreshTokenRequestDto(session.getRefreshToken()), getJsonOnlyHeaders(), ErrorDto.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private LoginResponseDto signIn(String username, String password) {
        ResponseEntity<LoginResponseDto> response = attemptLogin(new LoginDto(username, password), LoginResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String resetTokenFor(String username) {
        String raw = passwordResetTokenGenerator.generateToken();
        passwordResetTokenRepository.save(PasswordResetTokenEntity.builder()
                .tokenHash(passwordResetTokenGenerator.hashToken(raw))
                .user(userRepository.findByUsername(username).orElseThrow())
                .requestedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .requestIp("127.0.0.1")
                .userAgent("JUnit")
                .build());
        return raw;
    }
}
