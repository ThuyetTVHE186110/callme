package com.callme.infrastructure.config;

import com.callme.common.port.AccountStatusPort;
import com.callme.common.response.ApiResponse;
import com.callme.infrastructure.security.JwtAuthenticationFilter;
import com.callme.infrastructure.security.JwtTokenProvider;
import com.callme.infrastructure.web.CorrelationIdFilter;
import com.callme.infrastructure.web.RateLimitFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT-based security: no sessions, no CSRF (there's no browser form to forge),
 * every endpoint requires a valid bearer token except registration/login. Auth/permission
 * failures are rendered as the same ApiResponse envelope the rest of the API uses, rather
 * than Spring Security's default HTML/whitebox pages.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Mobile clients (the actual target audience — see CLAUDE.md G.3) don't send
     * an `Origin` header and are unaffected by CORS; this exists for whatever web
     * console (CSKH/admin dashboard, partner integrations) ends up calling the API
     * from a browser. Externalized per-profile so dev can stay permissive
     * (`http://localhost:*`) while prod is locked to the real deployed origin(s) —
     * never the wildcard `*`, which Spring rejects outright once credentials are
     * allowed (and a Bearer-token API always implies credentialed requests).
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(java.time.Duration.ofHours(1));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider, ObjectMapper objectMapper,
                                                    CorsConfigurationSource corsConfigurationSource,
                                                    AccountStatusPort accountStatusPort) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // springdoc — disabled outright in prod via `springdoc.*.enabled` (404s
                        // either way), permitted here so dev/staging can actually reach it
                        // without a token; nothing sensitive lives at these paths.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // `management.endpoints.web.exposure.include` (application.yml) already
                        // narrows what's exposed here to just `health` (orchestrator probes) and
                        // `prometheus` (metrics scrape) — both consumed by infrastructure, not
                        // end users, and both reached only from inside the deployment network in
                        // practice (load balancer/ingress never routes public traffic to /actuator/**).
                        // No JWT story makes sense for either consumer, so they're permitted here.
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> writeJsonError(response, objectMapper, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) -> writeJsonError(response, objectMapper, 403, "Access denied")))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, accountStatusPort), UsernamePasswordAuthenticationFilter.class)
                // Runs first (outermost) so the id is in MDC — and on the response —
                // for the entire request, including auth failures the JWT filter itself raises.
                .addFilterBefore(new CorrelationIdFilter(), JwtAuthenticationFilter.class)
                // Runs *after* JWT auth so booking/location rules can key on the
                // authenticated profile id rather than a shared NAT/proxy IP.
                .addFilterAfter(new RateLimitFilter(objectMapper), JwtAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
    }
}
