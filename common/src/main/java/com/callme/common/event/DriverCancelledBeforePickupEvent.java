package com.callme.common.event;

import java.util.UUID;

/**
 * CLAUDE.md B.4 — "tài xế hủy ngay sau khi nhận (trước khi đến điểm đón)": the
 * driver backed out before reaching the customer, i.e. before taking the wheel of
 * the customer's car. Unlike a customer-initiated cancellation, this must NOT end
 * the customer's request — it must trigger a fresh search for a replacement driver.
 *
 * Published by the trip module (which also publishes the matching {@link TripCancelledEvent}
 * to release the withdrawing driver back into the pool); consumed by booking to
 * re-run matching for the same pickup/destination without the customer having to
 * re-enter anything.
 */
public record DriverCancelledBeforePickupEvent(UUID bookingId, UUID tripId, UUID previousDriverId) {
}
