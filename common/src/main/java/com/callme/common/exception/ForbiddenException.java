package com.callme.common.exception;

/** Thrown when an authenticated account tries to act on a resource it does not own (someone else's booking, trip, ...). Maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
