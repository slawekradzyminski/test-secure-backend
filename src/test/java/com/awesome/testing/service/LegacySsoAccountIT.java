package com.awesome.testing.service;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.mfa.MfaSetupResponseDto;
import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUser;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accounts provisioned by the removed SSO exchange keep auth_provider/provider_subject in app_user and
 * only an unusable random password. They must recover through the ordinary password reset and may set up
 * two-factor authentication like any local account.
 */
@SuppressWarnings("ConstantConditions")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class LegacySsoAccountIT extends DomainHelper {

    private static final String FORGOT_ENDPOINT = "/api/v1/users/password/forgot";
    private static final String RESET_ENDPOINT = "/api/v1/users/password/reset";
    private static final String SETUP_2FA_ENDPOINT = "/api/v1/users/2fa/setup";
    private static final String SSO_EXCHANGE_ENDPOINT = "/api/v1/users/sso/exchange";
    private static final String NEW_PASSWORD = "recovered-password";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void aFormerSsoAccountRecoversThroughPasswordResetAndCanSetUpTwoFactor() {
        // given
        UserEntity user = legacySsoUser();

        // when
        ResponseEntity<Map<String, String>> forgot = executePost(FORGOT_ENDPOINT,
                new ForgotPasswordRequestDto(user.getEmail()), getJsonOnlyHeaders(), mapTypeReference());
        ResponseEntity<String> reset = executePost(RESET_ENDPOINT,
                new ResetPasswordRequestDto(forgot.getBody().get("token"), NEW_PASSWORD, NEW_PASSWORD),
                getJsonOnlyHeaders(), String.class);
        ResponseEntity<LoginResponseDto> login = attemptLogin(
                new LoginDto(user.getUsername(), NEW_PASSWORD), LoginResponseDto.class);
        ResponseEntity<MfaSetupResponseDto> setup = executePost(SETUP_2FA_ENDPOINT, null,
                getHeadersWith(login.getBody().getToken()), MfaSetupResponseDto.class);

        // then
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(forgot.getBody().get("token")).isNotBlank();
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setup.getBody().getSecret()).isNotBlank();
    }

    @Test
    void theRemovedSsoExchangeEndpointIsNoLongerPublicAndNotFound() {
        // given
        String token = getToken(getRandomUser());

        // when
        ResponseEntity<String> anonymous = executePost(SSO_EXCHANGE_ENDPOINT,
                "{\"idToken\":\"anything\"}", getJsonOnlyHeaders(), String.class);
        ResponseEntity<String> authenticated = executePost(SSO_EXCHANGE_ENDPOINT,
                "{\"idToken\":\"anything\"}", getHeadersWith(token), String.class);

        // then
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authenticated.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UserEntity legacySsoUser() {
        String name = "sso-" + UUID.randomUUID().toString().substring(0, 12);
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder().username(name)
                .email(name + "@example.com")
                .password(passwordEncoder.encode("sso-" + UUID.randomUUID()))
                .roles(List.of(Role.ROLE_CLIENT)).firstName("Former").lastName("Sso").build());
        jdbcTemplate.update("UPDATE app_user SET auth_provider = 'keycloak', provider_subject = ? WHERE id = ?",
                UUID.randomUUID().toString(), user.getId());
        return user;
    }
}
