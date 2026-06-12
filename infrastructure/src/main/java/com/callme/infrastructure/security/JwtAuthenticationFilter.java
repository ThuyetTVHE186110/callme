package com.callme.infrastructure.security;

import com.callme.common.port.AccountStatusPort;
import com.callme.common.security.AuthenticatedAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the `Authorization: Bearer <token>` header, validates it, and — when valid —
 * populates the SecurityContext with an AuthenticatedAccount principal so downstream
 * controllers can resolve "who is making this request" via @AuthenticationPrincipal
 * instead of trusting customerId/driverId fields in the request body.
 *
 * A missing or invalid token simply leaves the context empty; SecurityConfig then
 * rejects the request with 401 if the endpoint requires authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccountStatusPort accountStatusPort;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccountStatusPort accountStatusPort) {
        this.tokenProvider = tokenProvider;
        this.accountStatusPort = accountStatusPort;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var parsed = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
                AuthenticatedAccount account = parsed.account();
                // A signed, unexpired token is necessary but not sufficient: the account
                // behind it must still be active AND the token must postdate the most
                // recent password change / suspension / log-out-everywhere (its embedded
                // tokenVersion matches the account's — CLAUDE.md §4.8.3). This per-request
                // check is what makes revocation immediate instead of whenever the 24h
                // token happens to expire — a JWT cannot be revoked, but its account can.
                if (!accountStatusPort.isTokenValid(account.accountId(), parsed.tokenVersion())) {
                    // OWASP A09 — a suspended/stale-credential session still presenting a
                    // live token is exactly the event revocation exists for; it must be visible.
                    log.warn("Rejected request from account {} presenting a revoked or deactivated token", account.accountId());
                    SecurityContextHolder.clearContext();
                } else {
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(account, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtTokenProvider.InvalidTokenException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
