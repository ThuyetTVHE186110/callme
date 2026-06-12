package com.callme.identity.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.UnauthorizedException;
import com.callme.common.port.SmsOtpPort;
import com.callme.identity.entity.OtpChallenge;
import com.callme.identity.entity.OtpPurpose;
import com.callme.identity.repository.OtpChallengeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md §4.8 — the OTP rules that carry the security weight: hashed at rest,
 * per-phone hourly issuance cap (anti SMS-pumping), per-code attempt cap that gates
 * even the RIGHT code once exhausted, single-use consumption, uniform failures.
 */
class OtpServiceImplTest {

    private static final String PHONE = "0901234567";

    private final OtpChallengeRepository repository = mock(OtpChallengeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SmsOtpPort smsOtpPort = mock(SmsOtpPort.class);
    private final OtpServiceImpl service = new OtpServiceImpl(repository, passwordEncoder, smsOtpPort);

    @Test
    void issuingStoresOnlyTheHashAndSendsTheRawCode() {
        when(repository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(PHONE), eq(OtpPurpose.REGISTRATION), any()))
                .thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(repository.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(PHONE, OtpPurpose.REGISTRATION);

        var sentCode = ArgumentCaptor.forClass(String.class);
        verify(smsOtpPort).sendOtp(eq(PHONE), sentCode.capture());
        assertThat(sentCode.getValue()).matches("\\d{6}");

        var saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCodeHash()).isEqualTo("hashed");
    }

    /** §4.8 — "3 OTP/giờ/SĐT": SMS-pumping costs real money per message. */
    @Test
    void theHourlyIssuanceCapRefusesTheFourthCode() {
        when(repository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(PHONE), eq(OtpPurpose.REGISTRATION), any()))
                .thenReturn((long) OtpChallenge.MAX_ISSUED_PER_HOUR);

        assertThatThrownBy(() -> service.issue(PHONE, OtpPurpose.REGISTRATION))
                .isInstanceOf(ConflictException.class);

        verify(smsOtpPort, never()).sendOtp(anyString(), anyString());
    }

    private OtpChallenge liveChallenge() {
        var challenge = new OtpChallenge(PHONE, OtpPurpose.REGISTRATION, "hashed", Instant.now());
        when(repository.findFirstByPhoneNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(challenge));
        when(repository.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return challenge;
    }

    @Test
    void theRightCodeConsumesTheChallenge() {
        var challenge = liveChallenge();
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        service.verify(PHONE, OtpPurpose.REGISTRATION, "123456");

        assertThat(challenge.getUsedAt()).isNotNull();
    }

    @Test
    void aWrongCodeBurnsAnAttemptAndFailsUniformly() {
        var challenge = liveChallenge();
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "000000"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(challenge.getAttempts()).isEqualTo(1);
        assertThat(challenge.getUsedAt()).isNull();
    }

    /** Even the RIGHT code is refused once the attempt budget was burned — the cap must stop an attacker, not merely slow them. */
    @Test
    void anExhaustedChallengeRefusesEvenTheRightCode() {
        var challenge = liveChallenge();
        for (int i = 0; i < OtpChallenge.MAX_ATTEMPTS; i++) {
            challenge.recordFailedAttempt();
        }
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "123456"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(challenge.getUsedAt()).isNull();
    }

    @Test
    void anExpiredChallengeIsRefused() {
        var challenge = new OtpChallenge(PHONE, OtpPurpose.REGISTRATION, "hashed",
                Instant.now().minus(OtpChallenge.TTL).minusSeconds(1));
        when(repository.findFirstByPhoneNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "123456"))
                .isInstanceOf(UnauthorizedException.class);
    }

    /** A registration code must never unlock a password reset — purposes are matched, not just phones. */
    @Test
    void aCodeForOnePurposeCannotUnlockAnother() {
        when(repository.findFirstByPhoneNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.PASSWORD_RESET, "123456"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
