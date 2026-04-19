package com.awesome.testing.service;

import com.awesome.testing.config.properties.SsoProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.security.oidc.OidcTokenVerifier;
import com.awesome.testing.security.oidc.OidcUserClaims;
import com.awesome.testing.service.token.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoLoginServiceTest {

    @Mock
    private OidcTokenVerifier oidcTokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    private SsoLoginService ssoLoginService;

    private SsoProperties ssoProperties;

    @BeforeEach
    void setUp() {
        ssoProperties = new SsoProperties();
        ssoProperties.setAuthProvider("keycloak");
        ssoLoginService = new SsoLoginService(
                ssoProperties,
                oidcTokenVerifier,
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService
        );
    }

    @Test
    void shouldProvisionNewSsoUserAndReturnAppTokens() {
        OidcUserClaims claims = claims("sso-subject", "Sso Client", "sso-client@example.com");
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "sso-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("sso-client@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("sso-client")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(jwtTokenProvider.createToken("sso-client", List.of(Role.ROLE_CLIENT))).thenReturn("app-jwt");
        UserEntity savedUser = UserEntity.builder()
                .username("sso-client")
                .email("sso-client@example.com")
                .password("encoded-password")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        when(refreshTokenService.createToken(any(UserEntity.class))).thenReturn(refreshToken(savedUser));

        LoginResponseDto response = ssoLoginService.exchange("id-token");

        assertThat(response.getToken()).isEqualTo("app-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUsername()).isEqualTo("sso-client");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity provisioned = captor.getValue();
        assertThat(provisioned.getAuthProvider()).isEqualTo("keycloak");
        assertThat(provisioned.getProviderSubject()).isEqualTo("sso-subject");
        assertThat(provisioned.getEmailVerified()).isTrue();
        assertThat(provisioned.getChatSystemPrompt()).isEqualTo(UserService.DEFAULT_CHAT_SYSTEM_PROMPT.strip());
        assertThat(provisioned.getToolSystemPrompt()).isEqualTo(UserService.DEFAULT_TOOL_SYSTEM_PROMPT.strip());

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(72);
    }

    @Test
    void shouldReuseExistingSsoUser() {
        OidcUserClaims claims = claims("sso-subject", "Sso Client", "updated@example.com");
        UserEntity existingUser = UserEntity.builder()
                .username("sso-client")
                .email("old@example.com")
                .password("encoded-password")
                .roles(List.of(Role.ROLE_CLIENT))
                .authProvider("keycloak")
                .providerSubject("sso-subject")
                .build();
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "sso-subject"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail("updated@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(jwtTokenProvider.createToken("sso-client", List.of(Role.ROLE_CLIENT))).thenReturn("app-jwt");
        when(refreshTokenService.createToken(existingUser)).thenReturn(refreshToken(existingUser));

        LoginResponseDto response = ssoLoginService.exchange("id-token");

        assertThat(response.getUsername()).isEqualTo("sso-client");
        assertThat(existingUser.getEmail()).isEqualTo("updated@example.com");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectExistingSsoUserEmailChangeWhenEmailBelongsToAnotherUser() {
        OidcUserClaims claims = claims("sso-subject", "Sso Client", "taken@example.com");
        UserEntity existingUser = UserEntity.builder()
                .id(1)
                .username("sso-client")
                .email("old@example.com")
                .password("encoded-password")
                .roles(List.of(Role.ROLE_CLIENT))
                .authProvider("keycloak")
                .providerSubject("sso-subject")
                .build();
        UserEntity otherUser = UserEntity.builder()
                .id(2)
                .username("other")
                .email("taken@example.com")
                .password("encoded-password")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "sso-subject"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> ssoLoginService.exchange("id-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Email is already used by another login method")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectEmailConflictWithPasswordUser() {
        OidcUserClaims claims = claims("sso-subject", "Sso Client", "existing@example.com");
        UserEntity passwordUser = UserEntity.builder()
                .username("existing")
                .email("existing@example.com")
                .password("encoded-password")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "sso-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(passwordUser));

        assertThatThrownBy(() -> ssoLoginService.exchange("id-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Email is already used by another login method")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldFallbackToSafeUsernameWhenClaimsContainOnlySymbols() {
        OidcUserClaims claims = claims("symbol-subject", "!!!", "!!!@example.com");
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "symbol-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("!!!@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("sso-user")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createToken("sso-user", List.of(Role.ROLE_CLIENT))).thenReturn("app-jwt");
        when(refreshTokenService.createToken(any(UserEntity.class))).thenReturn(refreshToken(null));

        LoginResponseDto response = ssoLoginService.exchange("id-token");

        assertThat(response.getUsername()).isEqualTo("sso-user");
    }

    @Test
    void shouldUseStableSuffixWhenUsernameCollides() {
        OidcUserClaims claims = claims("sso-subject", "sso-client", "sso-client@example.com");
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("keycloak", "sso-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("sso-client@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("sso-client")).thenReturn(true);
        when(userRepository.existsByUsername("sso-client-992739")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createToken("sso-client-992739", List.of(Role.ROLE_CLIENT))).thenReturn("app-jwt");
        when(refreshTokenService.createToken(any(UserEntity.class))).thenReturn(refreshToken(null));

        LoginResponseDto response = ssoLoginService.exchange("id-token");

        assertThat(response.getUsername()).isEqualTo("sso-client-992739");
    }

    private OidcUserClaims claims(String subject, String username, String email) {
        return new OidcUserClaims(subject, username, email, "Sso", "Client", true, null);
    }

    @Test
    void shouldUseIdentityProviderFromClaimsWhenPresent() {
        OidcUserClaims claims = new OidcUserClaims(
                "google-subject", "google-user", "google-user@gmail.com",
                "Google", "User", true, "google"
        );
        when(oidcTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByAuthProviderAndProviderSubject("google", "google-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("google-user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("google-user")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createToken("google-user", List.of(Role.ROLE_CLIENT))).thenReturn("app-jwt");
        when(refreshTokenService.createToken(any(UserEntity.class))).thenReturn(refreshToken(null));

        LoginResponseDto response = ssoLoginService.exchange("id-token");

        assertThat(response.getUsername()).isEqualTo("google-user");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity provisioned = captor.getValue();
        assertThat(provisioned.getAuthProvider()).isEqualTo("google");
        assertThat(provisioned.getProviderSubject()).isEqualTo("google-subject");
    }

    private RefreshTokenEntity refreshToken(UserEntity user) {
        return RefreshTokenEntity.builder()
                .token("refresh-token")
                .expiresAt(Instant.now().plusSeconds(60))
                .user(user)
                .build();
    }

}
