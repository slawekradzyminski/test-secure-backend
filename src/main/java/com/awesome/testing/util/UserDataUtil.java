package com.awesome.testing.util;

import java.util.List;

import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;

public class UserDataUtil {

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