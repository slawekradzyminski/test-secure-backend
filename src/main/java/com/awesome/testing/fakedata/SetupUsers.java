package com.awesome.testing.fakedata;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
public class SetupUsers {

    private final UserService userService;

    @Transactional
    public void createUsers() {
        User admin = SetupUsers.createAdminUser(
                "admin",
                "admin",
                "awesome@testing.com",
                "Slawomir",
                "Radzyminski"
        );
        userService.signup(admin);

        User admin2 = SetupUsers.createAdminUser(
                "admin2",
                "admin2",
                "john.doe@company.com",
                "John",
                "Doe"
        );
        userService.signup(admin2);

        User client1 = SetupUsers.createClientUser(
                "client",
                "client",
                "alice.smith@yahoo.com",
                "Alice",
                "Smith"
        );
        userService.signup(client1);

        User client2 = SetupUsers.createClientUser(
                "client2",
                "client2",
                "bob.johnson@google.com",
                "Bob",
                "Johnson"
        );
        userService.signup(client2);

        User client3 = SetupUsers.createClientUser(
                "client3",
                "client3",
                "charlie.brown@example.com",
                "Charlie",
                "Brown"
        );
        userService.signup(client3);
    }

    public static User createAdminUser(String username, String password, String email, String firstName, String lastName) {
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(password);
        admin.setEmail(email);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setRoles(List.of(Role.ROLE_ADMIN));
        return admin;
    }

    public static User createClientUser(String username, String password, String email, String firstName, String lastName) {
        User client = new User();
        client.setUsername(username);
        client.setPassword(password);
        client.setEmail(email);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setRoles(List.of(Role.ROLE_CLIENT));
        return client;
    }
}