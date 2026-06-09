package com.callme.common.exception;

/** Thrown when a requested resource (booking, trip, driver, ...) does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
