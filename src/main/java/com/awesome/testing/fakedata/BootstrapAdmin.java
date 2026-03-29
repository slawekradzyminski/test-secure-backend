package com.awesome.testing.fakedata;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdmin implements CommandLineRunner {

    private static final int MIN_PASSWORD_LENGTH = 16;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;
    private final String firstName;
    private final String lastName;

    public BootstrapAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.first-name:Admin}") String firstName,
            @Value("${app.bootstrap-admin.last-name:User}") String lastName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public void run(String... args) {
        validateConfiguration();

        userRepository.findByUsernameOrEmail(username, email)
                .ifPresentOrElse(this::handleExistingUser, this::createAdminUser);
    }

    private void handleExistingUser(UserEntity existingUser) {
        boolean sameIdentity = username.equals(existingUser.getUsername()) && email.equals(existingUser.getEmail());
        boolean isAdmin = existingUser.getRoles() != null && existingUser.getRoles().contains(Role.ROLE_ADMIN);

        if (sameIdentity && isAdmin) {
            log.info("Bootstrap admin '{}' already exists, skipping creation", username);
            return;
        }

        throw new IllegalStateException("Bootstrap admin configuration conflicts with an existing non-admin user");
    }

    private void createAdminUser() {
        UserEntity admin = UserEntity.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(firstName)
                .lastName(lastName)
                .build();
        userRepository.save(admin);
        log.info("Created bootstrap admin '{}'", username);
    }

    private void validateConfiguration() {
        if (username.isBlank()) {
            throw new IllegalStateException("app.bootstrap-admin.username must be configured when bootstrap admin is enabled");
        }
        if (email.isBlank()) {
            throw new IllegalStateException("app.bootstrap-admin.email must be configured when bootstrap admin is enabled");
        }
        if (password.isBlank()) {
            throw new IllegalStateException("app.bootstrap-admin.password must be configured when bootstrap admin is enabled");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.bootstrap-admin.password must be at least 16 characters long");
        }
    }
}
