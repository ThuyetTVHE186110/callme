package com.callme.identity.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.UnauthorizedException;
import com.callme.common.port.DriverRegistrationPort;
import com.callme.common.security.AccountRole;
import com.callme.identity.dto.AuthResponse;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.RegisterCustomerRequest;
import com.callme.identity.dto.RegisterRequest;
import com.callme.identity.entity.Account;
import com.callme.identity.repository.AccountRepository;
import com.callme.identity.service.AuthService;
import com.callme.identity.service.CustomerService;
import com.callme.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final DriverRegistrationPort driverRegistrationPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthServiceImpl(AccountRepository accountRepository,
                           CustomerService customerService,
                           DriverRegistrationPort driverRegistrationPort,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.driverRegistrationPort = driverRegistrationPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        String token = tokenProvider.generate(account.getId(), profileId, account.getRole());
        return new AuthResponse(token, account.getId(), profileId, account.getRole());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        var account = accountRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new UnauthorizedException("Số điện thoại hoặc mật khẩu không đúng"));

        if (!account.isActive() || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("Số điện thoại hoặc mật khẩu không đúng");
        }

        String token = tokenProvider.generate(account.getId(), account.getProfileId(), account.getRole());
        return new AuthResponse(token, account.getId(), account.getProfileId(), account.getRole());
    }
}
