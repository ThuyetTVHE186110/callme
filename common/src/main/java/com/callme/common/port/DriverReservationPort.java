package com.callme.common.port;

import java.util.UUID;

/**
 * Published by the driver module; consumed by dispatch to atomically claim a
 * candidate driver during matching (CLAUDE.md line 59 / B.1 — "ai chạm trước thắng").
 * Reserving as part of matching — before a booking is ever persisted as CONFIRMED —
 * is what closes the race window: two concurrent bookings can no longer both walk
 * away believing they got the same driver.
 */
public interface DriverReservationPort {

    /**
     * Attempts to claim the driver for a new trip. Returns {@code false} (rather than
     * throwing) when the driver is offline or already committed elsewhere — including
     * when an optimistic-lock conflict reveals a concurrent reservation won the race —
     * so the caller can simply fall through to the next candidate.
     */
    boolean tryReserve(UUID driverId);

    /**
     * Compensating action for when a reservation was claimed but the booking that
     * claimed it could not be persisted/confirmed afterwards — without this, a
     * storage failure between "reserve" and "save" would strand the driver
     * permanently unmatchable (reserved for a trip that will never exist).
     */
    void release(UUID driverId);
}
