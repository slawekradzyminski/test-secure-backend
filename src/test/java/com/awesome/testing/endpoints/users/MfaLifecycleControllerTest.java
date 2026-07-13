package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.mfa.MfaChallengeRequestDto;
import com.awesome.testing.dto.mfa.MfaCodeRequestDto;
import com.awesome.testing.dto.mfa.MfaProtectedActionRequestDto;
import com.awesome.testing.dto.mfa.MfaRecoveryCodesResponseDto;
import com.awesome.testing.dto.mfa.MfaSetupResponseDto;
import com.awesome.testing.dto.mfa.MfaStatusResponseDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.MfaChallengeEntity;
import com.awesome.testing.entity.MfaCredentialEntity;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.MfaChallengeRepository;
import com.awesome.testing.repository.MfaCredentialRepository;
import com.awesome.testing.repository.MfaRecoveryCodeRepository;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.mfa.TotpService;
import com.awesome.testing.service.token.PasswordResetTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class MfaLifecycleControllerTest extends DomainHelper {

    private static final String STATUS_ENDPOINT = "/api/v1/users/2fa/status";
    private static final String SETUP_ENDPOINT = "/api/v1/users/2fa/setup";
    private static final String CONFIRM_ENDPOINT = "/api/v1/users/2fa/confirm";
    private static final String COMPLETE_ENDPOINT = "/api/v1/users/signin/2fa";
    private static final String RECOVERY_CODES_ENDPOINT = "/api/v1/users/2fa/recovery-codes";
    private static final String DISABLE_ENDPOINT = "/api/v1/users/2fa/disable";
    private static final String PASSWORD_RESET_ENDPOINT = "/api/v1/users/password/reset";

    @Autowired
    private TotpService totpService;

    @Autowired
    @Qualifier("mfaClock")
    private Clock clock;

    @Autowired
    private MfaChallengeRepository challengeRepository;

    @Autowired
    private MfaCredentialRepository credentialRepository;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordResetTokenGenerator passwordResetTokenGenerator;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldEnrollAndCompleteLoginWithTotpWithoutIssuingTokensAfterPasswordOnly() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        MfaRecoveryCodesResponseDto recovery = confirm(account.initialLogin().getToken(), setup.getSecret());

        assertThat(recovery.getRecoveryCodes()).hasSize(8).doesNotHaveDuplicates();
        MfaStatusResponseDto status = getStatus(account.initialLogin().getToken());
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getUnusedRecoveryCodes()).isEqualTo(8);

        LoginResponseDto passwordResponse = passwordLogin(account);
        assertThat(passwordResponse.isMfaRequired()).isTrue();
        assertThat(passwordResponse.getChallengeToken()).isNotBlank();
        assertThat(passwordResponse.getChallengeExpiresAt()).isAfter(clock.instant());
        assertThat(passwordResponse.getToken()).isNull();
        assertThat(passwordResponse.getRefreshToken()).isNull();

        String nextCode = codeAtStep(setup.getSecret(), currentStep() + 1);
        ResponseEntity<LoginResponseDto> completed = executePost(
                COMPLETE_ENDPOINT,
                new MfaChallengeRequestDto(passwordResponse.getChallengeToken(), nextCode),
                getJsonOnlyHeaders(),
                LoginResponseDto.class);

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody()).isNotNull();
        assertThat(completed.getBody().getToken()).isNotBlank();
        assertThat(completed.getBody().getRefreshToken()).isNotBlank();

        ResponseEntity<ErrorDto> replay = executePost(
                COMPLETE_ENDPOINT,
                new MfaChallengeRequestDto(passwordResponse.getChallengeToken(), nextCode),
                getJsonOnlyHeaders(),
                ErrorDto.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowEachRecoveryCodeOnlyOnce() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        String recoveryCode = confirm(account.initialLogin().getToken(), setup.getSecret()).getRecoveryCodes().getFirst();

        LoginResponseDto firstChallenge = passwordLogin(account);
        ResponseEntity<LoginResponseDto> firstLogin = complete(firstChallenge.getChallengeToken(), recoveryCode,
                LoginResponseDto.class);
        assertThat(firstLogin.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResponseDto secondChallenge = passwordLogin(account);
        ResponseEntity<ErrorDto> reused = complete(secondChallenge.getChallengeToken(), recoveryCode, ErrorDto.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MfaStatusResponseDto status = getStatus(firstLogin.getBody().getToken());
        assertThat(status.getUnusedRecoveryCodes()).isEqualTo(7);
    }

    @Test
    void shouldInvalidateAnOlderChallengeWhenPasswordLoginIsRepeated() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        confirm(account.initialLogin().getToken(), setup.getSecret());

        LoginResponseDto olderChallenge = passwordLogin(account);
        LoginResponseDto newerChallenge = passwordLogin(account);
        String code = codeAtStep(setup.getSecret(), currentStep() + 1);

        ResponseEntity<ErrorDto> invalidated = complete(
                olderChallenge.getChallengeToken(), code, ErrorDto.class);
        assertThat(invalidated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<LoginResponseDto> completed = complete(
                newerChallenge.getChallengeToken(), code, LoginResponseDto.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().getToken()).isNotBlank();
    }

    @Test
    void shouldReplaceRecoveryCodesAndDisableMfaWithStrongReauthentication() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        List<String> originalCodes = confirm(account.initialLogin().getToken(), setup.getSecret()).getRecoveryCodes();

        String nextCode = codeAtStep(setup.getSecret(), currentStep() + 1);
        ResponseEntity<MfaRecoveryCodesResponseDto> replacement = executePost(
                RECOVERY_CODES_ENDPOINT,
                new MfaProtectedActionRequestDto(account.password(), nextCode),
                getHeadersWith(account.initialLogin().getToken()),
                MfaRecoveryCodesResponseDto.class);
        assertThat(replacement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replacement.getBody().getRecoveryCodes()).hasSize(8).doesNotContainAnyElementsOf(originalCodes);

        LoginResponseDto challenge = passwordLogin(account);
        ResponseEntity<ErrorDto> obsoleteCode = complete(
                challenge.getChallengeToken(), originalCodes.getFirst(), ErrorDto.class);
        assertThat(obsoleteCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String replacementCode = replacement.getBody().getRecoveryCodes().getFirst();
        ResponseEntity<Void> disabled = executePost(
                DISABLE_ENDPOINT,
                new MfaProtectedActionRequestDto(account.password(), replacementCode),
                getHeadersWith(account.initialLogin().getToken()),
                Void.class);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResponseDto normalLogin = passwordLogin(account);
        assertThat(normalLogin.isMfaRequired()).isFalse();
        assertThat(normalLogin.getToken()).isNotBlank();
        assertThat(getStatus(normalLogin.getToken()).isEnabled()).isFalse();
    }

    @Test
    void shouldRequireAuthenticationForMfaManagementAndRejectInvalidConfirmationCode() {
        ResponseEntity<ErrorDto> anonymous = executePost(
                SETUP_ENDPOINT, null, getJsonOnlyHeaders(), ErrorDto.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        TestAccount account = createAccount();
        startSetup(account.initialLogin().getToken());
        ResponseEntity<ErrorDto> invalid = executePost(
                CONFIRM_ENDPOINT,
                new MfaCodeRequestDto("000000"),
                getHeadersWith(account.initialLogin().getToken()),
                ErrorDto.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectExpiredSetupAndExpiredLoginChallenge() {
        TestAccount account = createAccount();
        MfaSetupResponseDto expiredSetup = startSetup(account.initialLogin().getToken());
        MfaCredentialEntity credential = credentialRepository.findByUserUsername(account.username()).orElseThrow();
        credential.setSetupExpiresAt(clock.instant().minusSeconds(1));
        credentialRepository.save(credential);

        ResponseEntity<ErrorDto> expiredConfirmation = executePost(
                CONFIRM_ENDPOINT,
                new MfaCodeRequestDto(codeAtStep(expiredSetup.getSecret(), currentStep())),
                getHeadersWith(account.initialLogin().getToken()),
                ErrorDto.class);
        assertThat(expiredConfirmation.getStatusCode()).isEqualTo(HttpStatus.GONE);

        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        confirm(account.initialLogin().getToken(), setup.getSecret());
        LoginResponseDto challengeResponse = passwordLogin(account);
        MfaChallengeEntity challenge = challengeRepository
                .findFirstByUserUsernameOrderByCreatedAtDesc(account.username())
                .orElseThrow();
        challenge.setExpiresAt(clock.instant().minusSeconds(1));
        challengeRepository.save(challenge);

        ResponseEntity<ErrorDto> expiredChallenge = complete(
                challengeResponse.getChallengeToken(),
                codeAtStep(setup.getSecret(), currentStep() + 1),
                ErrorDto.class);
        assertThat(expiredChallenge.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldPreserveMfaRequirementAfterPasswordReset() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        confirm(account.initialLogin().getToken(), setup.getSecret());

        UserEntity user = userRepository.findByUsername(account.username()).orElseThrow();
        String rawResetToken = passwordResetTokenGenerator.generateToken();
        passwordResetTokenRepository.save(PasswordResetTokenEntity.builder()
                .tokenHash(passwordResetTokenGenerator.hashToken(rawResetToken))
                .user(user)
                .requestedAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(300))
                .requestIp("127.0.0.1")
                .userAgent("JUnit")
                .build());

        String newPassword = "UpdatedPassword123!";
        ResponseEntity<Void> reset = executePost(
                PASSWORD_RESET_ENDPOINT,
                new ResetPasswordRequestDto(rawResetToken, newPassword, newPassword),
                getJsonOnlyHeaders(),
                Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        TestAccount resetAccount = new TestAccount(account.username(), newPassword, account.initialLogin());
        LoginResponseDto loginAfterReset = passwordLogin(resetAccount);
        assertThat(loginAfterReset.isMfaRequired()).isTrue();
        assertThat(loginAfterReset.getToken()).isNull();
        assertThat(loginAfterReset.getRefreshToken()).isNull();
    }

    @Test
    void shouldDeleteAllMfaStateWhenTheAccountIsDeleted() {
        TestAccount account = createAccount();
        MfaSetupResponseDto setup = startSetup(account.initialLogin().getToken());
        confirm(account.initialLogin().getToken(), setup.getSecret());
        passwordLogin(account);

        assertThat(credentialRepository.findByUserUsername(account.username())).isPresent();
        assertThat(challengeRepository.countByUserUsername(account.username())).isPositive();
        assertThat(recoveryCodeRepository.countByCredentialUserUsername(account.username())).isEqualTo(8);

        ResponseEntity<Void> deleted = executeDelete(
                getUserEndpoint(account.username()) + "/right-to-be-forgotten",
                getHeadersWith(account.initialLogin().getToken()),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(credentialRepository.findByUserUsername(account.username())).isEmpty();
        assertThat(challengeRepository.countByUserUsername(account.username())).isZero();
        assertThat(recoveryCodeRepository.countByCredentialUserUsername(account.username())).isZero();
    }

    private TestAccount createAccount() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto initialLogin = registerAndLogin(user);
        return new TestAccount(user.getUsername(), user.getPassword(), initialLogin);
    }

    private MfaSetupResponseDto startSetup(String token) {
        ResponseEntity<MfaSetupResponseDto> response = executePost(
                SETUP_ENDPOINT, null, getHeadersWith(token), MfaSetupResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSecret()).matches("[A-Z2-7]{32}");
        assertThat(response.getBody().getOtpAuthUri()).startsWith("otpauth://totp/");
        assertThat(response.getBody().getQrCodeDataUri()).startsWith("data:image/png;base64,");
        return response.getBody();
    }

    private MfaRecoveryCodesResponseDto confirm(String token, String secret) {
        String code = codeAtStep(secret, currentStep());
        ResponseEntity<MfaRecoveryCodesResponseDto> response = executePost(
                CONFIRM_ENDPOINT,
                new MfaCodeRequestDto(code),
                getHeadersWith(token),
                MfaRecoveryCodesResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MfaStatusResponseDto getStatus(String token) {
        return executeGet(STATUS_ENDPOINT, getHeadersWith(token), MfaStatusResponseDto.class).getBody();
    }

    private LoginResponseDto passwordLogin(TestAccount account) {
        ResponseEntity<LoginResponseDto> response = attemptLogin(
                new LoginDto(account.username(), account.password()), LoginResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private <T> ResponseEntity<T> complete(String challenge, String code, Class<T> responseType) {
        return executePost(COMPLETE_ENDPOINT, new MfaChallengeRequestDto(challenge, code),
                getJsonOnlyHeaders(), responseType);
    }

    private long currentStep() {
        return clock.instant().getEpochSecond() / 30;
    }

    private String codeAtStep(String secret, long step) {
        return totpService.generateCode(secret, Instant.ofEpochSecond(step * 30));
    }

    private record TestAccount(String username, String password, LoginResponseDto initialLogin) {
    }
}
