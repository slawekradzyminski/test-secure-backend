package com.awesome.testing.fakedata;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Demo accounts of the seeding profiles. Runs on every start, so it tolerates whatever users did with the
 * accounts since the last start: a deleted demo user whose username or email now belongs to another
 * account is skipped (its getter returns null) instead of failing the start on a unique constraint.
 */
@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class SetupUsers {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Getter private UserEntity adminUser;
    @Getter private UserEntity clientUser;
    @Getter private UserEntity client2User;
    @Getter private UserEntity client3User;
    @Getter private UserEntity warehouseUser;

    @Transactional
    public void createUsers() {
        adminUser = seed(user("admin", "LocalDemoAdmin123!", "awesome@testing.com", "Slawomir", "Radzyminski",
                Role.ROLE_ADMIN));
        clientUser = seed(user("client", "client", "alice.smith@yahoo.com", "Alice", "Smith", Role.ROLE_CLIENT));
        client2User = seed(user("client2", "client2", "bob.johnson@google.com", "Bob", "Johnson", Role.ROLE_CLIENT));
        client3User = seed(user("client3", "client3", "charlie.brown@example.com", "Charlie", "Brown",
                Role.ROLE_CLIENT));
        warehouseUser = seed(user("warehouse", "warehouse", "warehouse@awesome-testing.test", "Wanda", "Picker",
                Role.ROLE_WAREHOUSE));
    }

    private UserEntity seed(DemoUser demo) {
        Optional<UserEntity> existing = userRepository.findByUsername(demo.username());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (userRepository.existsByUsernameIgnoringCase(demo.username())
                || userRepository.existsByEmailIgnoringCase(demo.email())) {
            log.warn("Skipping demo user '{}': its username or email belongs to another account", demo.username());
            return null;
        }
        return userRepository.save(UserEntity.builder()
                .username(demo.username())
                .password(passwordEncoder.encode(demo.password()))
                .email(demo.email())
                .firstName(demo.firstName())
                .lastName(demo.lastName())
                .roles(List.of(demo.role()))
                .build());
    }

    private static DemoUser user(String username, String password, String email, String firstName, String lastName,
                                 Role role) {
        return new DemoUser(username, password, email, firstName, lastName, role);
    }

    private record DemoUser(String username, String password, String email, String firstName, String lastName,
                            Role role) {
    }
}
