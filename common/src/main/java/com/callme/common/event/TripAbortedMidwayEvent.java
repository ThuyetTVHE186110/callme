package com.callme.common.event;

import com.callme.common.shared.GeoPoint;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md E.2 — the driver aborted an IN_PROGRESS trip after first bringing the
 * customer's car (and the customer) to a declared safe location. Carries that
 * location and both participant ids so listeners can act fast: notification alerts
 * both sides, and CSKH's worklist (see {@code EmergencyAbortReport}) gets exactly
 * what it needs to judge whether a replacement driver must be sent to the spot —
 * "có thể cần điều phối tài xế thay thế đến tiếp ứng tại chỗ" is a "may", not an
 * automatic dispatch, precisely because only a human can weigh the situation.
 */
public record TripAbortedMidwayEvent(UUID reportId, UUID tripId, UUID customerId, UUID driverId,
                                      GeoPoint safeLocation, Instant abortedAt, String note) {
}
