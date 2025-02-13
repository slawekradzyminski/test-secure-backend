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
    @Getter private UserEntity admin2User;
    @Getter private UserEntity clientUser;
    @Getter private UserEntity client2User;
    @Getter private UserEntity client3User;

    @Transactional
    public void createUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        adminUser = createAdminUser(
                "admin",
                "admin",
                "awesome@testing.com",
                "Slawomir",
                "Radzyminski"
        );
        userRepository.save(adminUser);

        admin2User = createAdminUser(
                "admin2",
                "admin2",
                "john.doe@company.com",
                "John",
                "Doe"
        );
        userRepository.save(admin2User);

        clientUser = createClientUser(
                "client",
                "client",
                "alice.smith@yahoo.com",
                "Alice",
                "Smith"
        );
        userRepository.save(clientUser);

        client2User = createClientUser(
                "client2",
                "client2",
                "bob.johnson@google.com",
                "Bob",
                "Johnson"
        );
        userRepository.save(client2User);

        client3User = createClientUser(
                "client3",
                "client3",
                "charlie.brown@example.com",
                "Charlie",
                "Brown"
        );
        userRepository.save(client3User);
    }

    private UserEntity createAdminUser(String username, String password, String email, String firstName, String lastName) {
        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setEmail(email);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setRoles(List.of(Role.ROLE_ADMIN));
        return admin;
    }

    private UserEntity createClientUser(String username, String password, String email, String firstName, String lastName) {
        UserEntity client = new UserEntity();
        client.setUsername(username);
        client.setPassword(passwordEncoder.encode(password));
        client.setEmail(email);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setRoles(List.of(Role.ROLE_CLIENT));
        return client;
    }
}