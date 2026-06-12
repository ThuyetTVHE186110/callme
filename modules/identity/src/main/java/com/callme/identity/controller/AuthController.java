package com.callme.identity.controller;

import com.callme.common.response.ApiResponse;
import com.callme.identity.dto.AuthResponse;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.PhoneNumberRequest;
import com.callme.identity.dto.RegisterRequest;
import com.callme.identity.dto.ResetPasswordRequest;
import com.callme.identity.dto.VerifyPhoneRequest;
import com.callme.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public entry points — the only endpoints permitted without a bearer token (see
 * SecurityConfig). Anything that requires an authenticated principal (e.g. change
 * password) lives under /api/accounts instead, NOT here: /api/auth/** is permitAll,
 * so an authenticated-only action placed here would silently run with no principal.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * CLAUDE.md §4.8.1 — creates a PENDING account and sends the activation OTP.
     * No token in the response anymore: the first token comes from {@link #verifyPhone}.
     */
    @PostMapping("/register")
    public ApiResponse<UUID> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** §4.8.1 — proves the phone, activates the account, returns the first token. */
    @PostMapping("/verify-phone")
    public ApiResponse<AuthResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return ApiResponse.ok(authService.verifyPhone(request));
    }

    /** §4.8.1 — lost/expired code; uniform 200 regardless of whether the phone has a pending account. */
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody PhoneNumberRequest request) {
        authService.resendVerificationOtp(request.phoneNumber());
        return ApiResponse.ok(null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** §4.8.2 — sends a PASSWORD_RESET OTP to the verified phone; uniform 200 (no enumeration). */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody PhoneNumberRequest request) {
        authService.requestPasswordReset(request.phoneNumber());
        return ApiResponse.ok(null);
    }

    /** §4.8.2 — OTP + new password; revokes every live token (§4.8.3), client logs in again. */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok(null);
    }
}
