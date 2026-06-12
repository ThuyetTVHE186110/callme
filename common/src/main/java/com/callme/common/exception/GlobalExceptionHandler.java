package com.callme.common.exception;

import com.callme.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * MDC key the correlation-id filter (`infrastructure.web.CorrelationIdFilter`)
     * stamps on every request — declared here, the lower-level module, so the filter
     * can depend on it without `common` reaching upward into `infrastructure`.
     */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Validation failed", fieldErrors));
    }

    /**
     * Business rule violations on a state transition (e.g. confirming an already-confirmed
     * payment, completing a trip that hasn't started). These are client errors caused by
     * acting on stale state, so they map to 409 rather than 500.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    /** Path variable không convert được sang kiểu yêu cầu (vd. UUID "null", số âm cho Long). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Tham số '" + ex.getName() + "' không hợp lệ: " + ex.getValue();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /**
     * CLAUDE.md §4.8 — the SMS gateway failed to take an OTP. 503 (not 500): this is
     * a known, transient upstream dependency failure the client should simply retry;
     * the registration/reset transaction already rolled back with it. Details go to
     * the log keyed by correlation id — never the response (the message could include
     * gateway internals).
     */
    @ExceptionHandler(SmsDeliveryException.class)
    public ResponseEntity<ApiResponse<Void>> handleSmsDelivery(SmsDeliveryException ex) {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        log.error("OTP SMS delivery failed [{}]", correlationId, ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Không gửi được tin nhắn xác thực — vui lòng thử lại sau ít phút"));
    }

    /**
     * Anything that escapes every other handler is, by definition, a bug or an
     * infrastructure failure — never something the client can act on. Leaking
     * {@code ex.getMessage()} (stack traces, SQL fragments, internal class names)
     * into the response body is an information-disclosure risk in production. We log
     * the real exception server-side keyed by a correlation id and hand the client
     * only that id — enough to report the issue to support without exposing internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        log.error("Unexpected error [{}]", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Đã xảy ra lỗi hệ thống — vui lòng thử lại sau hoặc liên hệ hỗ trợ với mã: " + correlationId));
    }
}
