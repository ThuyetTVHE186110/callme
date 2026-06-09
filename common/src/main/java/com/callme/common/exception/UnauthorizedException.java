package com.callme.common.exception;

/** Thrown for authentication failures (bad credentials, expired/invalid token). Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
