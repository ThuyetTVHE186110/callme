package com.callme;

import com.callme.common.exception.UnauthorizedException;
import com.callme.common.port.AccountStatusPort;
import com.callme.common.security.AccountRole;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.RegisterRequest;
import com.callme.identity.dto.ResetPasswordRequest;
import com.callme.identity.dto.VerifyPhoneRequest;
import com.callme.identity.service.AuthService;
import com.callme.infrastructure.security.JwtTokenProvider;
import com.callme.infrastructure.sms.LoggingSmsOtpPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md §4.8 end-to-end against a real PostgreSQL: register → (login refused) →
 * OTP verify → first token; forgot-password → reset → old password AND every
 * previously issued token dead, new login works. Exercises the full wiring a unit
 * test can't: OtpService + LoggingSmsOtpPort (the dev/test fake retaining codes),
 * Account.tokenVersion, and AccountStatusPort — the same check the JWT filter runs
 * per request.
 */
class AuthLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String PHONE = "0911222333";
    private static final String PASSWORD = "first-password-1";
    private static final String NEW_PASSWORD = "second-password-2";

    @Autowired private AuthService authService;
    @Autowired private AccountStatusPort accountStatusPort;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private LoggingSmsOtpPort smsOtpPort;

    @Test
    @Transactional
    void registrationIsGatedByOtpAndPasswordResetRevokesEveryLiveToken() {
        // --- Register: account is PENDING, no token yet (§4.8.1) ---
        var accountId = authService.register(
                new RegisterRequest("Nguyễn Văn G", PHONE, PASSWORD, "vang@example.com", AccountRole.CUSTOMER));
        assertThat(accountId).isNotNull();

        // --- Login before verification is refused even with the right password ---
        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);

        // --- Verify with the OTP the fake gateway "sent" → first token ---
        String code = smsOtpPort.lastCodeFor(PHONE);
        assertThat(code).matches("\\d{6}");
        var firstAuth = authService.verifyPhone(new VerifyPhoneRequest(PHONE, code));
        assertThat(firstAuth.token()).isNotBlank();

        // The freshly issued token passes the same per-request check the JWT filter runs.
        var firstParsed = tokenProvider.parse(firstAuth.token());
        assertThat(accountStatusPort.isTokenValid(accountId, firstParsed.tokenVersion())).isTrue();

        // --- Ordinary login now works ---
        var loginAuth = authService.login(new LoginRequest(PHONE, PASSWORD));
        assertThat(loginAuth.token()).isNotBlank();

        // --- Forgot password: OTP to the verified phone, then reset (§4.8.2) ---
        authService.requestPasswordReset(PHONE);
        String resetCode = smsOtpPort.lastCodeFor(PHONE);
        authService.resetPassword(new ResetPasswordRequest(PHONE, resetCode, NEW_PASSWORD));

        // --- §4.8.3 — every token issued before the reset is revoked... ---
        assertThat(accountStatusPort.isTokenValid(accountId, firstParsed.tokenVersion())).isFalse();

        // --- ...the old password is dead, and the new one logs in with a valid token ---
        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);
        var freshAuth = authService.login(new LoginRequest(PHONE, NEW_PASSWORD));
        var freshParsed = tokenProvider.parse(freshAuth.token());
        assertThat(accountStatusPort.isTokenValid(accountId, freshParsed.tokenVersion())).isTrue();
    }

    /** A registration OTP must not be replayable: once consumed, the same code is refused. */
    @Test
    @Transactional
    void aConsumedOtpCannotBeReplayed() {
        String phone = "0911444555";
        authService.register(new RegisterRequest("Nguyễn Văn H", phone, PASSWORD, null, AccountRole.CUSTOMER));
        String code = smsOtpPort.lastCodeFor(phone);

        authService.verifyPhone(new VerifyPhoneRequest(phone, code));

        assertThatThrownBy(() -> authService.verifyPhone(new VerifyPhoneRequest(phone, code)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
