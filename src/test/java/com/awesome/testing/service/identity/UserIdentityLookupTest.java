package com.awesome.testing.service.identity;

import com.awesome.testing.dto.user.IdentityIssue;
import com.awesome.testing.dto.user.UserIdentityIssueDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIdentityLookupTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void anExactUsernameWinsOverCaseVariants() {
        UserEntity exact = user(1, "Abc1", "a@example.com");
        when(userRepository.findByUsername("Abc1")).thenReturn(Optional.of(exact));

        assertThat(new UserIdentity(userRepository).findBySignInName("Abc1")).contains(exact);
        verify(userRepository, never()).findAllByUsernameIgnoringCase("Abc1");
    }

    @Test
    void aCaseVariantResolvesOnlyWhenUnambiguous() {
        UserEntity stored = user(1, "Stored1", "a@example.com");
        when(userRepository.findByUsername("stored1")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoringCase("stored1")).thenReturn(List.of(stored));
        when(userRepository.findByUsername("twin1")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoringCase("twin1"))
                .thenReturn(List.of(user(2, "Twin1", "b@example.com"), user(3, "TWIN1", "c@example.com")));

        UserIdentity identity = new UserIdentity(userRepository);

        assertThat(identity.findBySignInName("stored1")).contains(stored);
        assertThat(identity.findBySignInName("twin1")).isEmpty();
        assertThat(identity.findBySignInName(null)).isEmpty();
    }

    @Test
    void anIdentifierWithAnAtSignIsAnEmailFirst() {
        UserEntity owner = user(1, "owner", "owner@example.com");
        when(userRepository.findAllByEmailIgnoringCase("owner@example.com")).thenReturn(List.of(owner));

        assertThat(new UserIdentity(userRepository).findByUsernameOrEmail("Owner@Example.com")).contains(owner);
        verify(userRepository, never()).findByUsername("Owner@Example.com");
    }

    @Test
    void anUnknownEmailFallsBackToALegacyUsernameWithAnAtSign() {
        UserEntity legacy = user(1, "legacy@example.org", "x@example.com");
        when(userRepository.findAllByEmailIgnoringCase("legacy@example.org")).thenReturn(List.of());
        when(userRepository.findByUsername("legacy@example.org")).thenReturn(Optional.of(legacy));

        assertThat(new UserIdentity(userRepository).findByUsernameOrEmail("legacy@example.org")).contains(legacy);
    }

    @Test
    void emailsAreCanonicalizedToTrimmedLowerCase() {
        assertThat(UserIdentity.canonicalEmail(" Alice@Example.COM ")).isEqualTo("alice@example.com");
        assertThat(UserIdentity.canonicalEmail(null)).isNull();
    }

    @Test
    void theReportListsEveryRuleAnAccountBreaks() {
        when(userRepository.findAllIdentityColumns()).thenReturn(List.of(
                new Object[] {1, "client", "alice@example.com"},
                new Object[] {2, "alice@example.com", "other@example.com"},
                new Object[] {3, "noemail", ""},
                new Object[] {4, "dup1", "Dup@Example.com"},
                new Object[] {5, "dup2", "dup@example.com"}));

        List<UserIdentityIssueDto> issues = new UserIdentityReport(userRepository).issues();

        assertThat(issues).containsExactly(
                new UserIdentityIssueDto(2, "alice@example.com", "other@example.com",
                        List.of(IdentityIssue.USERNAME_FORMAT, IdentityIssue.USERNAME_IS_ANOTHER_USERS_EMAIL)),
                new UserIdentityIssueDto(3, "noemail", "", List.of(IdentityIssue.EMAIL_INVALID)),
                new UserIdentityIssueDto(4, "dup1", "Dup@Example.com",
                        List.of(IdentityIssue.EMAIL_NOT_CANONICAL, IdentityIssue.EMAIL_CASE_DUPLICATE)),
                new UserIdentityIssueDto(5, "dup2", "dup@example.com", List.of(IdentityIssue.EMAIL_CASE_DUPLICATE)));
    }

    private static UserEntity user(int id, String username, String email) {
        return UserEntity.builder().id(id).username(username).email(email).password("x").build();
    }
}
