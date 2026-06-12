package com.callme.identity.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.UnauthorizedException;
import com.callme.common.port.SmsOtpPort;
import com.callme.identity.entity.OtpChallenge;
import com.callme.identity.entity.OtpPurpose;
import com.callme.identity.repository.OtpChallengeRepository;
import com.callme.identity.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@Transactional
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    /** One uniform rejection for every verification failure — reveals nothing about which stage said no (OWASP A07). */
    private static final String VERIFY_FAILED_MESSAGE = "Mã OTP không đúng hoặc đã hết hạn";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsOtpPort smsOtpPort;

    public OtpServiceImpl(OtpChallengeRepository otpChallengeRepository, PasswordEncoder passwordEncoder, SmsOtpPort smsOtpPort) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsOtpPort = smsOtpPort;
    }

    @Override
    public void issue(String phoneNumber, OtpPurpose purpose) {
        var now = Instant.now();
        long issuedLastHour = otpChallengeRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(
                phoneNumber, purpose, now.minus(Duration.ofHours(1)));
        if (issuedLastHour >= OtpChallenge.MAX_ISSUED_PER_HOUR) {
            // OWASP A09 — pumping a number is a paid-abuse signal worth seeing.
            log.warn("OTP issuance cap hit for phone {} (purpose {})", maskPhone(phoneNumber), purpose);
            throw new ConflictException("Đã yêu cầu quá nhiều mã OTP cho số điện thoại này — vui lòng thử lại sau");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpChallengeRepository.save(new OtpChallenge(phoneNumber, purpose, passwordEncoder.encode(code), now));
        smsOtpPort.sendOtp(phoneNumber, code);
        log.info("OTP issued for phone {} (purpose {})", maskPhone(phoneNumber), purpose);
    }

    @Override
    public void verify(String phoneNumber, OtpPurpose purpose, String code) {
        var now = Instant.now();
        var challenge = otpChallengeRepository
                .findFirstByPhoneNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(phoneNumber, purpose)
                .orElseThrow(() -> failed(phoneNumber, purpose, "no live challenge"));

        if (challenge.isExpired(now)) {
            throw failed(phoneNumber, purpose, "challenge expired");
        }
        if (challenge.isExhausted()) {
            // The attempt budget gates the CODE, not the phone: even the right code is
            // refused once 5 guesses were burned — a guessed-on-the-6th-try code must
            // not work, or the cap would only slow an attacker down, not stop them.
            throw failed(phoneNumber, purpose, "attempt cap reached");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            otpChallengeRepository.save(challenge);
            throw failed(phoneNumber, purpose, "wrong code (attempt " + challenge.getAttempts() + ")");
        }

        challenge.markUsed(now);
        otpChallengeRepository.save(challenge);
        log.info("OTP verified for phone {} (purpose {})", maskPhone(phoneNumber), purpose);
    }

    private UnauthorizedException failed(String phoneNumber, OtpPurpose purpose, String detail) {
        // Detail goes to the log (A09); the client always sees the same message (A07).
        log.warn("OTP verification failed for phone {} (purpose {}): {}", maskPhone(phoneNumber), purpose, detail);
        return new UnauthorizedException(VERIFY_FAILED_MESSAGE);
    }

    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 3) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
