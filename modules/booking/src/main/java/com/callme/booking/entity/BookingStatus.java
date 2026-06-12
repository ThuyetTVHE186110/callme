package com.callme.booking.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    /** No driver was available within the search radius at request time (CLAUDE.md A.1) — distinct from PENDING so the customer gets clear feedback instead of silently waiting forever. */
    NO_DRIVER_FOUND,
    CANCELLED,
    /**
     * The ride actually happened — the trip for this booking reached COMPLETED.
     * Without this terminal state a finished booking would sit in CONFIRMED forever,
     * permanently tripping the one-active-booking rule (CLAUDE.md A.6) and locking
     * the customer out of ever booking again.
     */
    COMPLETED
}
