package com.callme.common.event;

import java.util.UUID;

/**
 * Published by the booking module once a customer (or admin) cancels a booking;
 * consumed by trip so an already-started trip for that booking is cancelled in lockstep
 * — without this, cancelling a CONFIRMED booking would orphan a STARTED/IN_PROGRESS trip
 * (and strand its driver permanently "on trip", since only trip-ended events release them).
 */
public record BookingCancelledEvent(UUID bookingId) {
}
