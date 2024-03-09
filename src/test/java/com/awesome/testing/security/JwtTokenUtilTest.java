package com.awesome.testing.security;

import com.awesome.testing.AbstractUnitTest;
import com.awesome.testing.exception.ApiException;
import com.awesome.testing.dto.users.Role;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class JwtTokenUtilTest extends AbstractUnitTest {

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    public void setup() {
        SecretKeyProvider secretKeyProvider = new SecretKeyProvider("4DZ3+asC4/EOVmPdsSFizGMBlxnws+CLgiX9I1hl3AA=");
        JwtParser jwtParser = Jwts.parser().verifyWith(secretKeyProvider.getSecretKey()).build();
        jwtTokenUtil = new JwtTokenUtil(jwtParser, secretKeyProvider, mock(MyUserDetails.class));
        ReflectionTestUtils.setField(jwtTokenUtil, "validityInMilliseconds", 3600000L);
    }

    @Test
    public void testValidateInvalidToken() {
        // given
        String invalidToken = "invalidToken";

        // when
        ThrowingCallable throwingCallable = () -> jwtTokenUtil.validateToken(invalidToken);

        // then
        assertThatThrownBy(throwingCallable).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @MethodSource("provideRolesForTest")
    public void testValidateToken(List<Role> roles) {
        // given
        String token = jwtTokenUtil.createToken("slawek", roles);

        // when
        boolean validationResult = jwtTokenUtil.validateToken(token);

        // then
        assertThat(validationResult).isTrue();
    }

    private static Stream<Arguments> provideRolesForTest() {
        return Stream.of(
                Arguments.of(List.of()),
                Arguments.of(List.of(Role.ROLE_ADMIN)),
                Arguments.of(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT)),
                Arguments.of(List.of(Role.ROLE_DOCTOR))
        );
    }

}
