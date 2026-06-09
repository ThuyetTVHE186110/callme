package com.callme.booking.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    /** No driver was available within the search radius at request time (CLAUDE.md A.1) — distinct from PENDING so the customer gets clear feedback instead of silently waiting forever. */
    NO_DRIVER_FOUND,
    CANCELLED
}
