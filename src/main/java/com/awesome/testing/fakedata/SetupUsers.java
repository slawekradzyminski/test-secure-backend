package com.awesome.testing.fakedata;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    @Transactional
    public void createUsers() {
        adminUser = userRepository.findByUsername("admin")
                .orElseGet(() -> userRepository.save(createAdminUser(
                        "admin",
                        "LocalDemoAdmin123!",
                        "awesome@testing.com",
                        "Slawomir",
                        "Radzyminski"
                )));

        clientUser = userRepository.findByUsername("client")
                .orElseGet(() -> userRepository.save(createClientUser(
                        "client",
                        "client",
                        "alice.smith@yahoo.com",
                        "Alice",
                        "Smith"
                )));

        client2User = userRepository.findByUsername("client2")
                .orElseGet(() -> userRepository.save(createClientUser(
                        "client2",
                        "client2",
                        "bob.johnson@google.com",
                        "Bob",
                        "Johnson"
                )));

        client3User = userRepository.findByUsername("client3")
                .orElseGet(() -> userRepository.save(createClientUser(
                        "client3",
                        "client3",
                        "charlie.brown@example.com",
                        "Charlie",
                        "Brown"
                )));
    }

    private UserEntity createAdminUser(String username, String password, String email, String firstName, String lastName) {
        return UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .roles(List.of(Role.ROLE_ADMIN))
                .build();
    }

    private UserEntity createClientUser(String username, String password, String email, String firstName, String lastName) {
        return UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }
}
