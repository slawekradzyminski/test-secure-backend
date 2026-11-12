package com.awesome.testing.service.identity;

import com.awesome.testing.dto.user.IdentityIssue;
import com.awesome.testing.dto.user.UserIdentityIssueDto;
import com.awesome.testing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lists accounts created before the identity rules that administrators may want to clean up. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIdentityReport {

    /** More accounts than this sharing a lower-cased value are case duplicates. */
    private static final long SINGLE = 1L;

    private final UserRepository userRepository;

    public List<UserIdentityIssueDto> issues() {
        List<Row> rows = userRepository.findAllIdentityColumns().stream()
                .map(columns -> new Row((Integer) columns[0], (String) columns[1], (String) columns[2]))
                .toList();
        Map<String, Long> usernames = count(rows, row -> lower(row.username()));
        Map<String, Long> emails = count(rows, row -> lower(row.email()));
        Set<String> canonicalEmails = emails.keySet();

        List<UserIdentityIssueDto> result = new ArrayList<>();
        for (Row row : rows) {
            Set<IdentityIssue> issues = EnumSet.noneOf(IdentityIssue.class);
            if (row.username() == null || !UserIdentity.CANONICAL_USERNAME.matcher(row.username()).matches()) {
                issues.add(IdentityIssue.USERNAME_FORMAT);
            }
            if (usernames.getOrDefault(lower(row.username()), 0L) > SINGLE) {
                issues.add(IdentityIssue.USERNAME_CASE_DUPLICATE);
            }
            if (row.username() != null && row.username().contains("@") && canonicalEmails.contains(lower(row.username()))
                    && !lower(row.username()).equals(lower(row.email()))) {
                issues.add(IdentityIssue.USERNAME_IS_ANOTHER_USERS_EMAIL);
            }
            if (invalidEmail(row.email())) {
                issues.add(IdentityIssue.EMAIL_INVALID);
            } else if (!row.email().equals(UserIdentity.canonicalEmail(row.email()))) {
                issues.add(IdentityIssue.EMAIL_NOT_CANONICAL);
            }
            if (emails.getOrDefault(lower(row.email()), 0L) > SINGLE) {
                issues.add(IdentityIssue.EMAIL_CASE_DUPLICATE);
            }
            if (!issues.isEmpty()) {
                result.add(new UserIdentityIssueDto(row.id(), row.username(), row.email(), List.copyOf(issues)));
            }
        }
        return result;
    }

    private static boolean invalidEmail(String email) {
        return email == null || email.isBlank() || email.indexOf('@') < 1
                || email.strip().codePoints().anyMatch(Character::isWhitespace);
    }

    private static Map<String, Long> count(List<Row> rows, Function<Row, String> key) {
        return rows.stream().map(key).filter(value -> value != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static String lower(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private record Row(Integer id, String username, String email) {
    }
}
