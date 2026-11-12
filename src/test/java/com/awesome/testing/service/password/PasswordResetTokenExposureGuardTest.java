package com.awesome.testing.service.password;

import com.awesome.testing.config.properties.PasswordResetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenExposureGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"server", "aitesters"})
    void refusesToStartWhenTheTokenIsExposedInASharedProfile(String sharedProfile) {
        // given
        PasswordResetTokenExposureGuard guard = guard(true, "local", sharedProfile);

        // when / then
        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("password-reset.expose-token-in-response must be false with the " + sharedProfile
                        + " profile: it returns reset tokens to any caller");
    }

    @Test
    void namesEverySharedProfileThatIsActive() {
        assertThatThrownBy(() -> PasswordResetTokenExposureGuard.check(true, "server", "docker", "aitesters"))
                .hasMessageContaining("with the aitesters, server profile");
    }

    @Test
    void allowsTheTokenInLocalAndTestProfiles() {
        assertThatCode(guard(true, "local")::verify).doesNotThrowAnyException();
        assertThatCode(guard(true, "test")::verify).doesNotThrowAnyException();
        assertThatCode(guard(true)::verify).doesNotThrowAnyException();
    }

    @Test
    void allowsSharedProfilesWhenTheTokenIsNotExposed() {
        assertThatCode(guard(false, "docker", "server")::verify).doesNotThrowAnyException();
        assertThatCode(guard(false, "local", "server")::verify).doesNotThrowAnyException();
        assertThatCode(guard(false, "aitesters")::verify).doesNotThrowAnyException();
    }

    private static PasswordResetTokenExposureGuard guard(boolean expose, String... profiles) {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setExposeTokenInResponse(expose);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new PasswordResetTokenExposureGuard(properties, environment);
    }
}
