package com.callme.common.event;

import com.callme.common.shared.CancellationReason;

import java.util.UUID;

/**
 * Published by the trip module whenever a trip ends without completing (cancelled
 * before or during the ride); consumed by driver to release the driver back into
 * the matching pool (CLAUDE.md line 59 / B.1) and by booking to close the booking
 * in lockstep — without depending on trip's internals.
 *
 * <p>{@code reason} lets booking decide what the trip's end means for the request:
 * driver-attributed withdrawals ({@code DRIVER_REQUEST}/{@code DRIVER_UNRESPONSIVE})
 * keep the booking alive for re-dispatch (CLAUDE.md B.4 — the matching
 * {@link DriverCancelledBeforePickupEvent} carries that flow), {@code SYSTEM_CASCADE}
 * means the booking itself initiated the cancellation (nothing left to do), and every
 * other reason ends the booking with the same classification so the E.1/E.3 fee policy
 * is applied once, in one place.
 */
public record TripCancelledEvent(UUID tripId, UUID bookingId, UUID driverId, CancellationReason reason) {
}
