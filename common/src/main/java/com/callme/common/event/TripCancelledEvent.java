package com.callme.common.event;

import java.util.UUID;

/**
 * Published by the trip module whenever a trip ends without completing (cancelled
 * before or during the ride); consumed by driver to release the driver back into
 * the matching pool (CLAUDE.md line 59 / B.1) without depending on trip's internals.
 */
public record TripCancelledEvent(UUID tripId, UUID bookingId, UUID driverId) {
}
