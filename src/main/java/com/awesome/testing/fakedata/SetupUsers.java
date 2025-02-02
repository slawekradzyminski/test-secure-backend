package com.awesome.testing.fakedata;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;
import com.awesome.testing.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
public class SetupUsers {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = createAdminUser(
                "admin",
                "admin",
                "awesome@testing.com",
                "Slawomir",
                "Radzyminski"
        );
        userRepository.save(admin);

        User admin2 = createAdminUser(
                "admin2",
                "admin2",
                "john.doe@company.com",
                "John",
                "Doe"
        );
        userRepository.save(admin2);

        User client1 = createClientUser(
                "client",
                "client",
                "alice.smith@yahoo.com",
                "Alice",
                "Smith"
        );
        userRepository.save(client1);

        User client2 = createClientUser(
                "client2",
                "client2",
                "bob.johnson@google.com",
                "Bob",
                "Johnson"
        );
        userRepository.save(client2);

        User client3 = createClientUser(
                "client3",
                "client3",
                "charlie.brown@example.com",
                "Charlie",
                "Brown"
        );
        userRepository.save(client3);
    }

    private User createAdminUser(String username, String password, String email, String firstName, String lastName) {
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setEmail(email);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setRoles(List.of(Role.ROLE_ADMIN));
        return admin;
    }

    private User createClientUser(String username, String password, String email, String firstName, String lastName) {
        User client = new User();
        client.setUsername(username);
        client.setPassword(passwordEncoder.encode(password));
        client.setEmail(email);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setRoles(List.of(Role.ROLE_CLIENT));
        return client;
    }
}