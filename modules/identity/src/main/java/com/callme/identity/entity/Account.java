package com.callme.identity.entity;

import com.callme.common.security.AccountRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The login identity behind a Customer or Driver profile. Kept separate from the
 * domain profile entities (Customer/Driver live in their own bounded contexts) so
 * identity owns "who can log in" while driver/identity own "who this person is in
 * the business domain". `profileId` bridges the two — it is the Customer.id for
 * CUSTOMER accounts, or the Driver.id for DRIVER accounts (see DriverRegistrationPort).
 */
@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "phoneNumber"))
@Getter
@NoArgsConstructor(force = true)
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    @Column(nullable = false)
    private UUID profileId;

    private boolean active;

    /**
     * CLAUDE.md §4.8.1 — when the phone number was proven via OTP; null means the
     * account is still pending and cannot log in (mirrors how a new Driver can't go
     * online until verified, §4.5). The phone is both the login identifier AND the
     * night-time emergency contact channel — an unproven one is unusable for either.
     */
    private Instant phoneVerifiedAt;

    /**
     * CLAUDE.md §4.8.3 — embedded as a JWT claim at issue time and compared on every
     * request ({@code AccountStatusPort.isTokenValid}). Bumping it revokes every token
     * issued before the bump: the chosen revocation mechanism (one column + one
     * comparison on the per-request lookup that already exists) over a blacklist
     * table that would need its own garbage collection.
     */
    private int tokenVersion;

    public Account(String phoneNumber, String passwordHash, AccountRole role, UUID profileId) {
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileId = profileId;
        this.active = true;
        this.tokenVersion = 0;
    }

    /** §4.8.1 — the OTP round-trip succeeded; the account becomes able to log in. */
    public void verifyPhone(Instant now) {
        if (this.phoneVerifiedAt != null) {
            throw new IllegalStateException("Phone already verified for account: " + this.id);
        }
        this.phoneVerifiedAt = now;
    }

    public boolean isPhoneVerified() {
        return this.phoneVerifiedAt != null;
    }

    public void deactivate() {
        if (!this.active) {
            throw new IllegalStateException("Account already deactivated: " + this.id);
        }
        this.active = false;
        // §4.8.3 — suspension also revokes live tokens: lifting it later must force a
        // fresh login, not silently resurrect sessions from before the incident.
        this.tokenVersion++;
    }

    /** Lifts a suspension — e.g. after CSKH resolves the incident that triggered it (CLAUDE.md C.4/F.1). */
    public void reactivate() {
        if (this.active) {
            throw new IllegalStateException("Account already active: " + this.id);
        }
        this.active = true;
    }

    /** §4.8.2 — every password change (reset or self-service) revokes all live tokens: a stolen session must not survive the recovery from its own theft. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
    }
}
