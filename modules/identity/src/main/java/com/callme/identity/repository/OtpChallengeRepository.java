package com.callme.identity.repository;

import com.callme.identity.entity.OtpChallenge;
import com.callme.identity.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /** The one challenge a submitted code is checked against — always the latest unconsumed issue for (phone, purpose). */
    Optional<OtpChallenge> findFirstByPhoneNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String phoneNumber, OtpPurpose purpose);

    /** Backs the §4.8 per-phone hourly issuance cap (anti SMS-pumping). */
    long countByPhoneNumberAndPurposeAndCreatedAtAfter(String phoneNumber, OtpPurpose purpose, Instant threshold);
}
