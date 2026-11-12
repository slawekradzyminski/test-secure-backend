package com.awesome.testing.service.identity;

import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Canonical form of usernames and emails. Usernames are stored as typed but are unique and looked up
 * without regard to case; new usernames use {@link #USERNAME_CHARACTERS}. Emails are stored lower-case
 * and compared without regard to case.
 *
 * <p>Accounts created before these rules may break them (case-variant duplicates, usernames with
 * spaces or '@'). They keep working: an exact username always wins, and a case-insensitive match is
 * used only when it is unambiguous. {@code GET /api/v1/admin/users/identity-issues} lists them.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIdentity {

    /** Characters allowed in a new username; the length is checked separately (4 to 255). */
    public static final String USERNAME_CHARACTERS = "^[A-Za-z0-9._-]*$";
    public static final String USERNAME_CHARACTERS_MESSAGE =
            "Username may only contain letters, digits, dots, underscores and hyphens";
    static final Pattern CANONICAL_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{4,255}$");

    private final UserRepository userRepository;

    public static String canonicalEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean usernameTaken(String username) {
        return userRepository.existsByUsernameIgnoringCase(username);
    }

    public boolean emailTaken(String email) {
        return userRepository.existsByEmailIgnoringCase(canonicalEmail(email));
    }

    public boolean emailTakenByAnother(String email, Integer userId) {
        return userRepository.existsByEmailIgnoringCaseAndIdNot(canonicalEmail(email), userId);
    }

    /** The account a sign-in name refers to: the exact username, else the only case-insensitive match. */
    public Optional<UserEntity> findBySignInName(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).or(() -> single(userRepository.findAllByUsernameIgnoringCase(username)));
    }

    /** The only account using this email (case-insensitively); empty when none or ambiguous. */
    public Optional<UserEntity> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return single(userRepository.findAllByEmailIgnoringCase(canonicalEmail(email)));
    }

    /**
     * Resolves a password-reset identifier by its format: with '@' it is an email (falling back to an
     * exact legacy username containing '@'), otherwise a username. Never throws for duplicates.
     */
    public Optional<UserEntity> findByUsernameOrEmail(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        if (identifier.contains("@")) {
            return findByEmail(identifier).or(() -> userRepository.findByUsername(identifier));
        }
        return findBySignInName(identifier);
    }

    private static Optional<UserEntity> single(List<UserEntity> matches) {
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
