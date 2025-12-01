package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.AuthenticationHandler;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.service.token.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationHandler authenticationHandler;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private UserRegisterDto registerDto;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        registerDto = UserRegisterDto.builder()
                .username("johndoe")
                .email("john.doe@example.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();

        userEntity = buildUserEntity("johndoe", "john.doe@example.com");
        userEntity.setRoles(List.of(Role.ROLE_CLIENT));
        userEntity.setSystemPrompt("Act cool");
    }

    @Test
    void shouldSignInWhenCredentialsAreValid() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));
        when(jwtTokenProvider.createToken(registerDto.getUsername(), userEntity.getRoles())).thenReturn("jwt-token");
        RefreshTokenEntity refreshTokenEntity = buildRefreshToken("refresh-token", userEntity);
        when(refreshTokenService.createToken(userEntity)).thenReturn(refreshTokenEntity);

        TokenPair tokens = userService.signIn(registerDto.getUsername(), registerDto.getPassword());

        verify(authenticationHandler).authUser(registerDto.getUsername(), registerDto.getPassword());
        assertThat(tokens.getToken()).isEqualTo("jwt-token");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldSignupNewUserWhenUnique() {
        when(userRepository.findByUsernameOrEmail(registerDto.getUsername(), registerDto.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("encoded");

        userService.signup(registerDto);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo(registerDto.getUsername());
        assertThat(saved.getEmail()).isEqualTo(registerDto.getEmail());
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getRoles()).containsExactly(Role.ROLE_CLIENT);
        assertThat(saved.getSystemPrompt()).isEqualTo(UserService.DEFAULT_SYSTEM_PROMPT.strip());
    }

    @Test
    void shouldThrowWhenSignupWithExistingUsername() {
        when(userRepository.findByUsernameOrEmail(registerDto.getUsername(), registerDto.getEmail()))
                .thenReturn(Optional.of(buildUserEntity(registerDto.getUsername(), "other@mail.com")));

        assertThatThrownBy(() -> userService.signup(registerDto))
                .isInstanceOf(CustomException.class)
                .hasMessage("Username is already in use");
    }

    @Test
    void shouldThrowWhenSignupWithExistingEmail() {
        when(userRepository.findByUsernameOrEmail(registerDto.getUsername(), registerDto.getEmail()))
                .thenReturn(Optional.of(buildUserEntity("other", registerDto.getEmail())));

        assertThatThrownBy(() -> userService.signup(registerDto))
                .isInstanceOf(CustomException.class)
                .hasMessage("Email is already in use");
    }

    @Test
    void shouldDeleteExistingUser() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        userService.delete(registerDto.getUsername());

        verify(refreshTokenService).removeAllTokensForUser(registerDto.getUsername());
        verify(userRepository).deleteByUsername(registerDto.getUsername());
    }

    @Test
    void shouldSearchExistingUser() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        UserEntity result = userService.search(registerDto.getUsername());

        assertThat(result).isEqualTo(userEntity);
    }

    @Test
    void shouldReturnCurrentUserInWhoAmI() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtTokenProvider.extractTokenFromRequest(request)).thenReturn("raw-token");
        when(jwtTokenProvider.getUsername("raw-token")).thenReturn(registerDto.getUsername());
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        UserEntity current = userService.whoAmI(request);

        assertThat(current).isEqualTo(userEntity);
    }

    @Test
    void shouldRefreshToken() {
        RefreshTokenEntity rotatedToken = buildRefreshToken("new-refresh", userEntity);
        when(refreshTokenService.rotateToken("old-refresh")).thenReturn(rotatedToken);
        when(jwtTokenProvider.createToken(userEntity.getUsername(), userEntity.getRoles())).thenReturn("refreshed");

        TokenPair tokenPair = userService.refresh("old-refresh");

        assertThat(tokenPair.getToken()).isEqualTo("refreshed");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void shouldLogoutAndRevokeRefreshToken() {
        userService.logout("refresh-token", "johndoe");

        verify(refreshTokenService).revokeToken("refresh-token", "johndoe");
    }

    @Test
    void shouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(userEntity));

        assertThat(userService.getAll()).containsExactly(userEntity);
    }

    @Test
    void shouldEditUserFieldsWhenProvided() {
        UserEditDto editDto = UserEditDto.builder()
                .email("new@mail.com")
                .firstName("NewFirst")
                .lastName("NewLast")
                .build();
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserEntity updated = userService.edit(registerDto.getUsername(), editDto);

        assertThat(updated.getEmail()).isEqualTo("new@mail.com");
        assertThat(updated.getFirstName()).isEqualTo("NewFirst");
        assertThat(updated.getLastName()).isEqualTo("NewLast");
    }

    @Test
    void shouldReturnSystemPrompt() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        assertThat(userService.getSystemPrompt(registerDto.getUsername())).isEqualTo("Act cool");
    }

    @Test
    void shouldReturnDefaultSystemPromptWhenUserHasNone() {
        userEntity.setSystemPrompt(null);
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        assertThat(userService.getSystemPrompt(registerDto.getUsername()))
                .isEqualTo(UserService.DEFAULT_SYSTEM_PROMPT.strip());
    }

    @Test
    void shouldUpdateSystemPrompt() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserEntity result = userService.updateSystemPrompt(registerDto.getUsername(), "New Prompt");

        assertThat(result.getSystemPrompt()).isEqualTo("New Prompt");
        verify(userRepository).save(userEntity);
    }

    @Test
    void shouldConfirmUserExists() {
        when(userRepository.findByUsername(registerDto.getUsername())).thenReturn(Optional.of(userEntity));

        assertThat(userService.exists(registerDto.getUsername())).isTrue();
    }

    private static UserEntity buildUserEntity(String username, String email) {
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPassword("secret");
        entity.setRoles(List.of(Role.ROLE_CLIENT));
        entity.setFirstName("John");
        entity.setLastName("Doe");
        return entity;
    }

    private static RefreshTokenEntity buildRefreshToken(String tokenValue, UserEntity owner) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setToken(tokenValue);
        token.setUser(owner);
        return token;
    }
}
