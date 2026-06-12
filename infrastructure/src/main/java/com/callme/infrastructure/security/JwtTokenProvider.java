package com.callme.infrastructure.security;

import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates the JWTs that authenticate every request after login/register.
 * The token carries accountId (subject), profileId and role as claims so the
 * JwtAuthenticationFilter can rebuild an AuthenticatedAccount without a DB round-trip
 * on every request.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_PROFILE_ID = "profileId";
    private static final String CLAIM_ROLE = "role";
    /** CLAUDE.md §4.8.3 — compared against Account.tokenVersion per request; a bump on the account revokes every earlier token. */
    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be configured with at least 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(UUID accountId, UUID profileId, AccountRole role, int tokenVersion) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(accountId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey);
        if (profileId != null) {
            builder.claim(CLAIM_PROFILE_ID, profileId.toString());
        }
        return builder.compact();
    }

    /**
     * The principal plus the token-revocation metadata the filter needs.
     * {@code tokenVersion} stays out of {@link AuthenticatedAccount} on purpose —
     * it means nothing to business code, only to the per-request validity check.
     */
    public record ParsedToken(AuthenticatedAccount account, int tokenVersion) {
    }

    /**
     * Returns the authenticated principal carried by the token; throws when the
     * token is malformed, expired or signed with a different key. Callers treat
     * any of those uniformly as "not authenticated" rather than 500ing.
     */
    public ParsedToken parse(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID accountId = UUID.fromString(claims.getSubject());
            String profileIdClaim = claims.get(CLAIM_PROFILE_ID, String.class);
            UUID profileId = profileIdClaim == null ? null : UUID.fromString(profileIdClaim);
            AccountRole role = AccountRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            // Tokens minted before the claim existed count as version 0 — matching the
            // column's backfill default, so pre-rollout sessions stay valid until the
            // first bump (§4.8.3).
            Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            return new ParsedToken(new AuthenticatedAccount(accountId, profileId, role), version == null ? 0 : version);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token: " + ex.getMessage());
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
