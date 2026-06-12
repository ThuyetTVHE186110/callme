package com.callme.identity.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.UnauthorizedException;
import com.callme.common.port.DriverRegistrationPort;
import com.callme.common.security.AccountRole;
import com.callme.identity.dto.AuthResponse;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.RegisterCustomerRequest;
import com.callme.identity.dto.RegisterRequest;
import com.callme.identity.dto.ResetPasswordRequest;
import com.callme.identity.dto.VerifyPhoneRequest;
import com.callme.identity.entity.Account;
import com.callme.identity.entity.OtpPurpose;
import com.callme.identity.repository.AccountRepository;
import com.callme.identity.service.AuthService;
import com.callme.identity.service.CustomerService;
import com.callme.identity.service.OtpService;
import com.callme.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final DriverRegistrationPort driverRegistrationPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final OtpService otpService;

    public AuthServiceImpl(AccountRepository accountRepository,
                           CustomerService customerService,
                           DriverRegistrationPort driverRegistrationPort,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           OtpService otpService) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.driverRegistrationPort = driverRegistrationPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.otpService = otpService;
    }

    @Override
    public UUID register(RegisterRequest request) {
        if (request.role() == AccountRole.ADMIN) {
            throw new IllegalArgumentException("Tài khoản ADMIN không thể tự đăng ký qua API công khai");
        }
        accountRepository.findByPhoneNumber(request.phoneNumber()).ifPresent(existing -> {
            throw new ConflictException("Số điện thoại đã được đăng ký: " + request.phoneNumber());
        });

        UUID profileId = switch (request.role()) {
            case CUSTOMER -> customerService.register(new RegisterCustomerRequest(request.fullName(), request.phoneNumber(), request.email()));
            case DRIVER -> driverRegistrationPort.registerDriver(request.fullName());
            case ADMIN -> throw new IllegalArgumentException("unreachable");
        };

        var account = new Account(request.phoneNumber(), passwordEncoder.encode(request.password()), request.role(), profileId);
        accountRepository.save(account);

        // CLAUDE.md §4.8.1 — no token yet: the account is PENDING until the phone is
        // proven. The OTP goes out in the same transaction as the account row, so a
        // failed send rolls the registration back rather than stranding a pending
        // account that never received its code.
        otpService.issue(request.phoneNumber(), OtpPurpose.REGISTRATION);
        log.info("Account {} (role {}) registered pending phone verification", account.getId(), account.getRole());
        return account.getId();
    }

    @Override
    public AuthResponse verifyPhone(VerifyPhoneRequest request) {
        // OTP first: a caller without a valid code learns nothing about whether this
        // phone has a pending account, a verified one, or none at all.
        otpService.verify(request.phoneNumber(), OtpPurpose.REGISTRATION, request.code());

        var account = accountRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new UnauthorizedException("Mã OTP không đúng hoặc đã hết hạn"));
        if (account.isPhoneVerified()) {
            throw new ConflictException("Tài khoản đã được xác minh — vui lòng đăng nhập");
        }
        account.verifyPhone(Instant.now());
        accountRepository.save(account);

        log.info("Account {} verified its phone — issuing first token", account.getId());
        String token = tokenProvider.generate(account.getId(), account.getProfileId(), account.getRole(), account.getTokenVersion());
        return new AuthResponse(token, account.getId(), account.getProfileId(), account.getRole());
    }

    @Override
    public void resendVerificationOtp(String phoneNumber) {
        var account = accountRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (account == null || account.isPhoneVerified()) {
            // Uniform 200 — this endpoint must not reveal which numbers have pending
            // accounts. The hourly issuance cap in OtpService bounds the SMS cost.
            log.info("Verification OTP re-send requested for a phone with no pending account — silently ignored");
            return;
        }
        otpService.issue(phoneNumber, OtpPurpose.REGISTRATION);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // OWASP A09 — authentication outcomes are logged server-side (masked phone)
        // so credential stuffing shows up on a dashboard instead of happening in
        // silence. The HTTP response stays deliberately uniform — logging is where
        // the detail goes, never the error message.
        var account = accountRepository.findByPhoneNumber(request.phoneNumber()).orElse(null);
        if (account == null) {
            log.warn("Login failed — unknown phone number {}", maskPhone(request.phoneNumber()));
            throw new UnauthorizedException("Số điện thoại hoặc mật khẩu không đúng");
        }
        if (!account.isActive()) {
            log.warn("Login refused — account {} is deactivated", account.getId());
            throw new UnauthorizedException("Số điện thoại hoặc mật khẩu không đúng");
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            log.warn("Login failed — wrong password for account {}", account.getId());
            throw new UnauthorizedException("Số điện thoại hoặc mật khẩu không đúng");
        }
        // After the password check, so only someone holding the correct credentials
        // ever sees this state — it's their own onboarding step, not an oracle.
        if (!account.isPhoneVerified()) {
            log.warn("Login refused — account {} has not verified its phone yet", account.getId());
            throw new UnauthorizedException("Tài khoản chưa xác minh số điện thoại — vui lòng nhập mã OTP đã gửi tới số của bạn");
        }

        log.info("Login succeeded for account {} (role {})", account.getId(), account.getRole());
        String token = tokenProvider.generate(account.getId(), account.getProfileId(), account.getRole(), account.getTokenVersion());
        return new AuthResponse(token, account.getId(), account.getProfileId(), account.getRole());
    }

    @Override
    public void requestPasswordReset(String phoneNumber) {
        var account = accountRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (account == null || !account.isPhoneVerified() || !account.isActive()) {
            // Uniform 200 — forgot-password must not double as an enumeration oracle
            // (CLAUDE.md §4.8.2). An unverified phone gets nothing either: the reset
            // channel IS the verified phone.
            log.info("Password reset requested for a phone with no eligible account — silently ignored");
            return;
        }
        otpService.issue(phoneNumber, OtpPurpose.PASSWORD_RESET);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        otpService.verify(request.phoneNumber(), OtpPurpose.PASSWORD_RESET, request.code());

        var account = accountRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new UnauthorizedException("Mã OTP không đúng hoặc đã hết hạn"));
        // changePassword bumps tokenVersion (§4.8.3) — every session alive before this
        // reset, including the attacker's if the reset is recovering from a theft,
        // dies on its next request.
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
        log.warn("Password reset completed for account {} — all previously issued tokens are now revoked", account.getId());
    }

    /** Last 3 digits only — enough to correlate repeated attempts, not enough to reconstruct the number from logs. */
    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 3) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
