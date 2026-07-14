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
    void shouldExtractTokenFromRequestHeader() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def");

        assertThat(jwtTokenProvider.extractTokenFromRequest(request)).isEqualTo("abc.def");
    }

    @Test
    void shouldThrowForInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken("invalid"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired token");
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
}
