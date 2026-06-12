package com.callme.identity.entity;

/**
 * CLAUDE.md §4.8 — what a one-time code is allowed to unlock. Kept separate so a
 * registration code can never be replayed to reset a password (and vice versa):
 * verification always matches on (phone, purpose), never phone alone.
 */
public enum OtpPurpose {
    /** Activates a freshly registered account (§4.8.1 — account is unusable until the phone is proven). */
    REGISTRATION,
    /** Authorises a forgot-password reset (§4.8.2 — delivered to the already-verified phone, never email). */
    PASSWORD_RESET
}
