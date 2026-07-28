package com.callme.infrastructure.web;

import com.callme.common.response.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Token-bucket throttling for the handful of endpoints where a runaway client
 * (buggy retry loop, scripted abuse, a phone stuck resending under flaky network —
 * CLAUDE.md A.3's exact scenario) could otherwise hammer the system or another
 * party's inbox:
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}, {@code /register} — keyed by client IP, since
 *       there's no authenticated identity yet; capped tightly to blunt credential
 *       stuffing / registration spam.</li>
 *   <li>{@code POST /api/bookings} — keyed by the authenticated customer; a phone
 *       resending a stuck booking request shouldn't be able to flood dispatch with
 *       near-duplicate searches (the idempotency key already collapses *identical*
 *       retries — this bounds the rate of *distinct* ones).</li>
 *   <li>{@code POST /api/locations/{driverId}} — keyed by the authenticated driver;
 *       generously sized around the documented push cadence (CLAUDE.md G.3:
 *       10–15s normally, 5s mid-trip) so a compliant client never notices it.</li>
 *   <li>{@code POST /api/locations/customers/{customerId}} — same shape, keyed by
 *       the authenticated customer. Reports are voluntary (no mounted device
 *       pushing on a fixed cadence like a driver's app), so the same generous cap
 *       is a ceiling, not a target rate.</li>
 * </ul>
 *
 * Single-instance MVP (per CLAUDE.md, this domain doesn't call for a multi-node
 * fleet yet) — an in-process bucket cache is correct; a distributed counter
 * (Redis) only becomes necessary once there's more than one app instance to share
 * limits across.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);

    private record Rule(String method, PathPattern pathPattern, boolean keyByAuthenticatedUser, int capacity, Duration window) {
        Bucket newBucket() {
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, window)))
                    .build();
        }
    }

    private static final PathPatternParser PATTERN_PARSER = PathPatternParser.defaultInstance;

    private static final List<Rule> RULES = List.of(
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/login"), false, 5, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/register"), false, 5, Duration.ofMinutes(1)),
            // CLAUDE.md §4.8 — OTP endpoints. The per-IP caps here blunt scripted abuse
            // from one source; the per-PHONE issuance cap (3/hour) lives in OtpService,
            // since one number pumped from many IPs is invisible to an IP-keyed bucket.
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/verify-phone"), false, 10, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/resend-verification"), false, 3, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/forgot-password"), false, 3, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/auth/reset-password"), false, 10, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/bookings"), true, 10, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/locations/{driverId}"), true, 20, Duration.ofMinutes(1)),
            new Rule("POST", PATTERN_PARSER.parse("/api/locations/customers/{customerId}"), true, 20, Duration.ofMinutes(1))
    );

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.method() + " " + rule.pathPattern().getPatternString() + ":" + identityKey(request, rule);
        Bucket bucket = buckets.computeIfAbsent(key, k -> rule.newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // OWASP A09 — a tripped limiter is a security signal (credential stuffing,
            // scripted abuse), not just flow control; without this line it fired silently.
            log.warn("Rate limit exceeded for {}", key);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Bạn đang thao tác quá nhanh — vui lòng thử lại sau ít phút")));
        }
    }

    private Rule matchRule(HttpServletRequest request) {
        var path = org.springframework.http.server.PathContainer.parsePath(request.getRequestURI());
        for (Rule rule : RULES) {
            if (rule.method().equals(request.getMethod()) && rule.pathPattern().matches(path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Anonymous endpoints (login/register) key on the remote address — the only
     * identity available pre-authentication. Authenticated endpoints key on the
     * profile id from the JWT (set by {@code JwtAuthenticationFilter}, which runs
     * before this filter — see {@code SecurityConfig}'s ordering): two drivers
     * behind the same NAT/proxy must not share one bucket, and one driver switching
     * networks mid-shift must not reset theirs.
     */
    private String identityKey(HttpServletRequest request, Rule rule) {
        if (rule.keyByAuthenticatedUser()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof com.callme.common.security.AuthenticatedAccount account) {
                return "user:" + account.profileId();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
