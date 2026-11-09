package com.awesome.testing.security;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static String configuredKey;
    private static String otherKey;

    @Mock
    private MyUserDetails myUserDetails;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @BeforeAll
    static void generateKeys() throws Exception {
        configuredKey = pkcs8(2048);
        otherKey = pkcs8(2048);
    }

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(jwtTokenProvider, "signingKey", configuredKey);
        ReflectionTestUtils.setField(jwtTokenProvider, "validityInMilliseconds", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "requireSecureKey", true);
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
    void shouldSignWithRs256AndNameTheKey() throws Exception {
        String token = jwtTokenProvider.createToken("john", List.of(Role.ROLE_CLIENT));
        var header = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8));

        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("kid").asText()).isEqualTo(jwtTokenProvider.keyId());
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
    void shouldPublishAJwksThatVerifiesIssuedTokens() throws Exception {
        String token = jwtTokenProvider.createToken("john", List.of(Role.ROLE_CLIENT));
        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = ((List<Map<String, Object>>) jwtTokenProvider.jwks().get("keys")).getFirst();

        RSAPublicKey published = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n"))),
                new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")))));

        assertThat(jwk).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("use", "sig")
                .containsEntry("kid", jwtTokenProvider.keyId());
        assertThat(jwk).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
        assertThat(Jwts.parser().verifyWith(published).build().parseSignedClaims(token).getPayload().getSubject())
                .isEqualTo("john");
    }

    @Test
    void shouldDeriveTheSameKeyIdFromTheSameKey() {
        JwtTokenProvider sameKey = provider(configuredKey, 3600000L);

        assertThat(sameKey.keyId()).isEqualTo(jwtTokenProvider.keyId()).hasSize(43);
        assertThat(provider(otherKey, 3600000L).keyId()).isNotEqualTo(jwtTokenProvider.keyId());
    }

    @Test
    void shouldAcceptPemWrappedKey() {
        String pem = "-----BEGIN PRIVATE KEY-----\n" + configuredKey + "\n-----END PRIVATE KEY-----\n";

        assertThat(provider(pem, 3600000L).keyId()).isEqualTo(jwtTokenProvider.keyId());
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
        String foreignToken = provider(otherKey, 3600000L).createToken("john", List.of(Role.ROLE_ADMIN));

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(foreignToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectHmacTokenEvenWhenTheSecretIsThePublicKey() {
        RSAPublicKey publicKey = (RSAPublicKey) ReflectionTestUtils.getField(jwtTokenProvider, "publicKey");
        String confused = Jwts.builder().subject("admin")
                .signWith(Keys.hmacShaKeyFor(publicKey.getEncoded()), Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(confused))
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
        String expiredToken = provider(configuredKey, -60000L).createToken("john", List.of(Role.ROLE_CLIENT));

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldRejectMissingKeyWhenSecureKeyIsRequired() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "signingKey", " ");
        ReflectionTestUtils.setField(provider, "requireSecureKey", true);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must be configured");
    }

    @Test
    void shouldGenerateEphemeralKeyWhenNoneIsConfiguredAndNoneIsRequired() {
        JwtTokenProvider first = provider("", 3600000L);
        JwtTokenProvider second = provider(null, 3600000L);
        String token = first.createToken("john", List.of(Role.ROLE_CLIENT));

        assertThat(first.validateToken(token)).isTrue();
        assertThat(first.keyId()).isNotEqualTo(second.keyId());
        assertThatThrownBy(() -> second.validateToken(token)).isInstanceOf(CustomException.class);
    }

    @Test
    void shouldRejectKeyThatIsNotBase64Pkcs8() {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "signingKey", "local-development-jwt-key-change-me");

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must be a base64 PKCS#8 RSA private key");
    }

    @Test
    void shouldRejectRsaKeyShorterThan2048Bits() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "signingKey", pkcs8(1024));

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing key must be an RSA key of at least 2048 bits");
    }

    private JwtTokenProvider provider(String signingKey, long validityInMilliseconds) {
        JwtTokenProvider provider = new JwtTokenProvider(myUserDetails);
        ReflectionTestUtils.setField(provider, "signingKey", signingKey);
        ReflectionTestUtils.setField(provider, "validityInMilliseconds", validityInMilliseconds);
        ReflectionTestUtils.setField(provider, "requireSecureKey", false);
        provider.init();
        return provider;
    }

    private static String pkcs8(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        KeyPair pair = generator.generateKeyPair();
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }
}
