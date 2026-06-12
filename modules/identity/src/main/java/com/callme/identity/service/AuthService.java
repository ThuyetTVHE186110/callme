package com.callme.identity.service;

import com.callme.identity.dto.AuthResponse;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.RegisterRequest;
import com.callme.identity.dto.ResetPasswordRequest;
import com.callme.identity.dto.VerifyPhoneRequest;

import java.util.UUID;

public interface AuthService {

    /**
     * CLAUDE.md §4.8.1 — creates the account + domain profile in PENDING state (phone
     * unverified — cannot log in) and sends a registration OTP. No token is issued
     * here anymore: the first token comes from {@link #verifyPhone}, once the phone
     * is proven. Returns the new accountId for support/debug correlation.
     */
    UUID register(RegisterRequest request);

    /** §4.8.1 — proves the phone with the registration OTP; activates the account and issues the first token. */
    AuthResponse verifyPhone(VerifyPhoneRequest request);

    /**
     * §4.8.1 — re-sends a registration OTP (lost SMS, expired code). Responds
     * uniformly whether or not the phone has a pending account — this endpoint must
     * not double as an account-enumeration oracle.
     */
    void resendVerificationOtp(String phoneNumber);

    /** Rejects unverified-phone accounts (§4.8.1) on top of the usual credential/active checks. */
    AuthResponse login(LoginRequest request);

    /**
     * §4.8.2 — starts forgot-password: sends a PASSWORD_RESET OTP to the phone IF it
     * belongs to a verified account; uniform response either way (no enumeration).
     */
    void requestPasswordReset(String phoneNumber);

    /** §4.8.2 — completes forgot-password: OTP + new password; revokes all live tokens (§4.8.3). The client logs in again. */
    void resetPassword(ResetPasswordRequest request);
}
