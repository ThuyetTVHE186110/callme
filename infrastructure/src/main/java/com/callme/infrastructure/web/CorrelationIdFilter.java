package com.callme.infrastructure.web;

import com.callme.common.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation id — reused from the client's
 * {@code X-Request-Id} header when present (so a mobile client's own crash report
 * and our server log can be joined on the same id), generated otherwise. Stored in
 * MDC so it rides along on every log line for the lifetime of the request (see
 * {@code logback-spring.xml}'s JSON encoder), echoed back in the response header so
 * the client can quote it when contacting support, and reused verbatim by
 * {@link com.callme.common.exception.GlobalExceptionHandler#handleUnexpected} as the
 * id it hands back on a 500 — one id ties the client-visible error, the structured
 * log line, and (if the client also logs it) the client-side report together.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = GlobalExceptionHandler.CORRELATION_ID_MDC_KEY;
    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
