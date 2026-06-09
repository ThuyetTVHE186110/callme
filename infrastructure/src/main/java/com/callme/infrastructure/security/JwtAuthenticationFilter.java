package com.callme.infrastructure.security;

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

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedAccount account = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(account, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtTokenProvider.InvalidTokenException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
