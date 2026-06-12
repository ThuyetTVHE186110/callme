package com.callme.common.exception;

/**
 * CLAUDE.md §4.8 — an outbound OTP SMS could not be delivered to the gateway
 * (HTTP failure, gateway-side rejection, timeout). Raised by {@code SmsOtpPort}
 * implementations; mapped to 503 so the client knows to simply retry — and,
 * because OTP issuing runs inside the registration/reset transaction, the
 * business state rolls back with it (no pending account stranded without a code).
 */
public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message) {
        super(message);
    }

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
