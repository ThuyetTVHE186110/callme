package com.callme.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md §4.8 — one issued one-time code. Append-only in spirit (mirrors the
 * worklist pattern): rows are never edited beyond their own attempt/consumption
 * bookkeeping, so the table doubles as the audit trail of every OTP ever sent —
 * which is also what the per-phone hourly issuance cap counts against.
 *
 * <p>The code itself is stored only as a BCrypt hash: a leaked database must not
 * be a stack of valid login codes (OWASP A02) — the same reason passwords are hashed.
 */
@Entity
@Table(name = "otp_challenges", indexes = {
        // Backs both hot queries: "latest unconsumed challenge for (phone, purpose)"
        // during verification, and the per-hour issuance count during issuing.
        @Index(name = "idx_otp_challenges_phone_purpose_created", columnList = "phone_number, purpose, created_at")
})
@Getter
@NoArgsConstructor(force = true)
public class OtpChallenge {

    /** §4.8 — "OTP 6 số, TTL 5 phút". */
    public static final Duration TTL = Duration.ofMinutes(5);

    /** §4.8 — "tối đa 5 lần thử/OTP": brute-forcing 10^6 codes at 5 tries per code is hopeless. */
    public static final int MAX_ATTEMPTS = 5;

    /** §4.8 — "3 OTP/giờ/SĐT": SMS-pumping costs real money per message (OWASP A04 abuse case). */
    public static final int MAX_ISSUED_PER_HOUR = 3;

    @Id
    @GeneratedValue
    private UUID id;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    private String codeHash;

    private Instant expiresAt;

    private int attempts;

    /** Set once on successful verification — a consumed code can never be replayed. Null while live. */
    private Instant usedAt;

    private Instant createdAt;

    public OtpChallenge(String phoneNumber, OtpPurpose purpose, String codeHash, Instant now) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = now.plus(TTL);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(this.expiresAt);
    }

    public boolean isExhausted() {
        return this.attempts >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt() {
        this.attempts++;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
