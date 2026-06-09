package com.callme.common.exception;

/** Thrown when a request conflicts with the current state of a resource (duplicate phone number, double-confirmation, ...). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
