package com.awesome.testing.fakedata;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupUsersTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateMissingDemoUsersWhenBootstrapAdminAlreadyExists() {
        UserEntity bootstrapAdmin = UserEntity.builder()
                .username("admin")
                .email("admin@example.com")
                .password("encoded-bootstrap-password")
                .roles(List.of(Role.ROLE_ADMIN))
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(bootstrapAdmin));
        when(userRepository.findByUsername("client")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("client2")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("client3")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("encoded-demo-password");

        SetupUsers setupUsers = new SetupUsers(userRepository, passwordEncoder);
        setupUsers.createUsers();

        assertThat(setupUsers.getAdminUser()).isSameAs(bootstrapAdmin);
        assertThat(setupUsers.getClientUser().getUsername()).isEqualTo("client");
        assertThat(setupUsers.getClient2User().getUsername()).isEqualTo("client2");
        assertThat(setupUsers.getClient3User().getUsername()).isEqualTo("client3");
        verify(userRepository, never()).save(bootstrapAdmin);
    }
}
