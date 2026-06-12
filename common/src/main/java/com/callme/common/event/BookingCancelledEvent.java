package com.callme.common.event;

import com.callme.common.shared.CancellationReason;

import java.util.UUID;

/**
 * Published by the booking module once a customer (or admin) cancels a booking;
 * consumed by trip so an already-started trip for that booking is cancelled in lockstep
 * — without this, cancelling a CONFIRMED booking would orphan a STARTED/IN_PROGRESS trip
 * (and strand its driver permanently "on trip", since only trip-ended events release them).
 *
 * <p>{@code reason} carries who initiated the cancellation so the trip module can veto
 * the cascade when it would be unsafe/abusive: a customer-initiated cancellation must
 * not tear down a trip whose driver is physically behind the wheel of their car
 * (CLAUDE.md E.2 / D — the "cancel just before arrival to dodge the fare" loophole).
 * The listener throwing rolls the whole booking cancellation back atomically (G.1).
 */
public record BookingCancelledEvent(UUID bookingId, CancellationReason reason) {
}
