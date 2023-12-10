package com.awesome.testing.security;

import com.awesome.testing.exception.CustomException;
import com.awesome.testing.model.Role;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class JwtTokenProviderTest {

    @Mock
    private MyUserDetails myUserDetails;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", "4DZ3+asC4/EOVmPdsSFizGMBlxnws+CLgiX9I1hl3AA=");
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
    public void testValidateTokenWithInvalidToken() {
        // given
        String invalidToken = "invalidToken";

        // when
        ThrowingCallable throwingCallable = () -> jwtTokenProvider.validateToken(invalidToken);

        // then
        assertThatThrownBy(throwingCallable).isInstanceOf(CustomException.class);
    }

}
