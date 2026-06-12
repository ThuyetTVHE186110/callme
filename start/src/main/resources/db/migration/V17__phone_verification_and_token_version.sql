-- CLAUDE.md §4.8 — phone verification (OTP-gated account activation) and token
-- revocation via per-account token versioning.

alter table accounts
    add column phone_verified_at timestamp(6) with time zone;
alter table accounts
    add column token_version integer not null default 0;

-- Grandfather every pre-rollout account in as verified: they were created when
-- registration had no OTP step, and locking existing users (incl. out-of-band
-- provisioned admins) out of login overnight is not an acceptable migration.
update accounts
set phone_verified_at = now()
where phone_verified_at is null;

-- §4.8 — one row per issued OTP; append-only in spirit (only its own attempt /
-- consumption bookkeeping is ever updated), doubling as the audit trail the
-- per-phone hourly issuance cap counts against. Codes are stored as BCrypt hashes
-- only — a leaked table must not be a stack of valid login codes.
create table otp_challenges (
    id              uuid not null,
    phone_number    varchar(255) not null,
    purpose         varchar(30) not null check (purpose in ('REGISTRATION', 'PASSWORD_RESET')),
    code_hash       varchar(100) not null,
    expires_at      timestamp(6) with time zone not null,
    attempts        integer not null default 0,
    used_at         timestamp(6) with time zone,
    created_at      timestamp(6) with time zone not null,
    primary key (id)
);

-- Backs both hot queries: latest unconsumed challenge for (phone, purpose) during
-- verification, and the per-hour issuance count during issuing.
create index idx_otp_challenges_phone_purpose_created on otp_challenges (phone_number, purpose, created_at);
