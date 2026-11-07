package com.awesome.testing.security;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @Mock
    private MyUserDetails myUserDetails;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", "test-jwt-key-with-at-least-32-bytes");
        ReflectionTestUtils.setField(jwtTokenProvider, "validityInMilliseconds", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "requireSecureKey", false);
        jwtTokenProvider.init();
    }

    @Test
    void shouldCreateAndValidateToken() {
        List<Role> roles = List.of(Role.ROLE_CLIENT);
        when(myUserDetails.loadUserByUsername("john"))
                .thenReturn(User.withUsername("john").password("pwd").roles("CLIENT").build());

        String token = jwtTokenProvider.createToken("john", roles);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("john");
        assertThat(jwtTokenProvider.getAuthentication(token).getPrincipal())
                .isInstanceOf(CustomPrincipal.class);
    }

    @Test
    void shouldPublishEveryRoleAsAuthorityInTokenPayload() throws Exception {
        String token = jwtTokenProvider.createToken("john", List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        var auth = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).get("auth");

        assertThat(auth).isNotNull();
        assertThat(auth.findValuesAsText("authority")).containsExactly("ROLE_ADMIN", "ROLE_CLIENT");
    }

    @Test
    void shouldExtractTokenFromRequestHeader() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def");

        assertThat(jwtTokenProvider.extractTokenFromRequest(request)).isEqualTo("abc.def");
    }

    @Test
    void shouldIgnoreNonBearerAuthorizationHeader() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Basic am9objpwd2Q=");

        assertThat(jwtTokenProvider.extractTokenFromRequest(request)).isNull();
    }

    @Test
    void shouldThrowForInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken("invalid"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtTokenProvider otherProvider = provider("another-jwt-key-with-at-least-32-bytes", 3600000L);
        String foreignToken = otherProvider.createToken("john", List.of(Role.ROLE_ADMIN));

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(foreignToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectTokenWithTamperedPayload() {
        String token = jwtTokenProvider.createToken("john", List.of(Role.ROLE_CLIENT));
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("\"john\"", "\"admin\"")
                        .getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(forgedPayload).isNotEqualTo(parts[1]);
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectUnsignedToken() {
        String token = jwtTokenProvider.createToken("john", List.of(Role.ROLE_CLIENT));
        String[] parts = token.split("\\.");
        String noneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String unsignedToken = noneHeader + "." + parts[1] + ".";

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(unsignedToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenProvider expiringProvider = provider("test-jwt-key-with-at-least-32-bytes", -60000L);
        String expiredToken = expiringProvider.createToken("john", List.of(Role.ROLE_CLIENT));

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectShortKeyWhenSecureKeyIsRequired() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", "a-random-but-too-short-key");
        ReflectionTestUtils.setField(provider, "requireSecureKey", true);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must contain at least 32 random bytes");
    }

    @Test
    void shouldAcceptLongDeploymentKeyWhenSecureKeyIsRequired() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", "deployment-specific-jwt-key-with-entropy-123");
        ReflectionTestUtils.setField(provider, "validityInMilliseconds", 3600000L);
        ReflectionTestUtils.setField(provider, "requireSecureKey", true);
        provider.init();

        assertThat(provider.validateToken(provider.createToken("john", List.of(Role.ROLE_CLIENT)))).isTrue();
    }

    @Test
    void shouldAcceptExactly32ByteKeyWhenSecureKeyIsRequired() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(provider, "requireSecureKey", true);

        provider.init();

        assertThat(ReflectionTestUtils.getField(provider, "key")).isNotNull();
    }

    @Test
    void shouldStretchShortDevelopmentKeyWhenSecureKeyIsNotRequired() {
        JwtTokenProvider provider = provider("secret-key", 3600000L);

        String token = provider.createToken("john", List.of(Role.ROLE_CLIENT));

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void shouldRejectKnownDevelopmentKeyWhenSecureKeyIsRequired() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", "secret-key");
        ReflectionTestUtils.setField(provider, "requireSecureKey", true);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must contain at least 32 random bytes");
    }

    @Test
    void shouldRejectMissingKeyInEveryEnvironment() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", " ");

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must be configured");
    }

    private JwtTokenProvider provider(String secretKey, long validityInMilliseconds) {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "secretKey", secretKey);
        ReflectionTestUtils.setField(provider, "validityInMilliseconds", validityInMilliseconds);
        ReflectionTestUtils.setField(provider, "requireSecureKey", false);
        provider.init();
        return provider;
    }
}
