package com.callme.identity.service;

import com.callme.identity.entity.OtpPurpose;

/**
 * CLAUDE.md §4.8 — issuing and verifying one-time codes. The per-phone issuance cap
 * lives HERE (counted in the database), deliberately not in {@code RateLimitFilter}:
 * the filter keys on IP/authenticated user, which cannot stop one phone number being
 * pumped from many IPs — the resource being protected is the SMS bill per number.
 */
public interface OtpService {

    /** Generates, stores (hashed) and sends a code. Throws 409 when the phone's hourly issuance cap is hit. */
    void issue(String phoneNumber, OtpPurpose purpose);

    /**
     * Validates {@code code} against the latest live challenge for (phone, purpose) and
     * consumes it on success. Every failure path — no challenge, expired, attempt cap
     * reached, wrong code — throws the SAME 401 message, so a caller probing the
     * endpoint learns nothing about which stage rejected them.
     */
    void verify(String phoneNumber, OtpPurpose purpose, String code);
}
