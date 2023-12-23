package com.awesome.testing.security;

import com.awesome.testing.exception.CustomException;
import com.awesome.testing.dto.users.Role;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    public void setup() {
        SecretKeyProvider secretKeyProvider = new SecretKeyProvider("4DZ3+asC4/EOVmPdsSFizGMBlxnws+CLgiX9I1hl3AA=");
        JwtParser jwtParser = Jwts.parser().verifyWith(secretKeyProvider.getSecretKey()).build();
        jwtTokenProvider = new JwtTokenProvider(jwtParser, secretKeyProvider, mock(MyUserDetails.class));
        ReflectionTestUtils.setField(jwtTokenProvider, "validityInMilliseconds", 3600000L);
    }

    @Test
    public void testValidateToken() {
        // given
        String token1 = jwtTokenProvider.createToken("slawek", List.of());
        String token2 = jwtTokenProvider.createToken("slawek", List.of(Role.ROLE_ADMIN));
        String token3 = jwtTokenProvider.createToken("slawek", List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT));

        // when
        boolean validationResult1 = jwtTokenProvider.validateToken(token1);
        boolean validationResult2 = jwtTokenProvider.validateToken(token2);
        boolean validationResult3 = jwtTokenProvider.validateToken(token3);

        // then
        assertThat(validationResult1).isTrue();
        assertThat(validationResult2).isTrue();
        assertThat(validationResult3).isTrue();
    }

    @Test
    public void testValidateInvalidToken() {
        // given
        String invalidToken = "invalidToken";

        // when
        ThrowingCallable throwingCallable = () -> jwtTokenProvider.validateToken(invalidToken);

        // then
        assertThatThrownBy(throwingCallable).isInstanceOf(CustomException.class);
    }

}
