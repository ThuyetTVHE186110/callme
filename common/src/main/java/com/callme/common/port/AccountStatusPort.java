package com.callme.common.port;

import java.util.UUID;

/**
 * Published by the identity module; consumed by the infrastructure JWT filter so
 * that deactivating an account — or bumping its token version (CLAUDE.md §4.8.3:
 * password change, suspension, "log out everywhere") — takes effect on the very
 * next request, not up to 24 hours later when the token finally expires. A JWT is
 * self-contained by design (no DB round-trip to parse it), which is exactly why it
 * cannot be revoked by itself; in a domain where a driver may be suspended
 * mid-shift over a safety incident (CLAUDE.md C.4/C.6), "the ban applies tomorrow"
 * is not an acceptable revocation story. One indexed primary-key lookup per
 * authenticated request is the deliberate price — the token-version comparison
 * rides the SAME lookup (one extra column read), which is why §4.8 chose it over a
 * blacklist table. Introduce a short-TTL cache inside the adapter if this ever
 * shows up in profiles, without touching the filter.
 */
public interface AccountStatusPort {

    /**
     * True only when the account exists, is active, AND {@code tokenVersion} matches
     * the account's current version — i.e. the token was issued after the most recent
     * password change / suspension / log-out-everywhere. A deleted account is treated
     * the same as a deactivated one.
     */
    boolean isTokenValid(UUID accountId, int tokenVersion);
}
