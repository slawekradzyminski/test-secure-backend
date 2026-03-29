package com.awesome.testing.fakedata;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateBootstrapAdminWhenMissing() throws Exception {
        BootstrapAdmin bootstrapAdmin = bootstrapAdmin("VerySecureAdmin123!");
        when(userRepository.findByUsernameOrEmail("admin", "admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("VerySecureAdmin123!")).thenReturn("encoded");

        bootstrapAdmin.run();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().getRoles()).containsExactly(Role.ROLE_ADMIN);
    }

    @Test
    void shouldSkipWhenMatchingAdminAlreadyExists() throws Exception {
        BootstrapAdmin bootstrapAdmin = bootstrapAdmin("VerySecureAdmin123!");
        when(userRepository.findByUsernameOrEmail("admin", "admin@example.com"))
                .thenReturn(Optional.of(UserEntity.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .password("encoded")
                        .roles(List.of(Role.ROLE_ADMIN))
                        .build()));

        bootstrapAdmin.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldFailWhenBootstrapAdminConflictsWithExistingUser() {
        BootstrapAdmin bootstrapAdmin = bootstrapAdmin("VerySecureAdmin123!");
        when(userRepository.findByUsernameOrEmail("admin", "admin@example.com"))
                .thenReturn(Optional.of(UserEntity.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .password("encoded")
                        .roles(List.of(Role.ROLE_CLIENT))
                        .build()));

        assertThatThrownBy(() -> bootstrapAdmin.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin configuration conflicts with an existing non-admin user");
    }

    @Test
    void shouldFailWhenPasswordIsTooShort() {
        BootstrapAdmin bootstrapAdmin = bootstrapAdmin("short-password");

        assertThatThrownBy(() -> bootstrapAdmin.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.bootstrap-admin.password must be at least 16 characters long");
    }

    private BootstrapAdmin bootstrapAdmin(String password) {
        return new BootstrapAdmin(
                userRepository,
                passwordEncoder,
                "admin",
                password,
                "admin@example.com",
                "Admin",
                "User"
        );
    }
}
