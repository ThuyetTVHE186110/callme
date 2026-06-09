package com.callme.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md C.7 — "mất tín hiệu GPS / kết nối mạng giữa hành trình": the driver's
 * last reported fix has gone stale mid-trip (driver physically holding the customer's
 * car — safety-relevant, not just a billing nuisance). Published once, right as the
 * trip's GPS trail crosses the staleness threshold (see {@code TripServiceImpl}'s
 * sweep — it deliberately does not re-fire every sweep cycle), so support and both
 * participants get a single, timely heads-up rather than a flood of repeats.
 */
public record GpsSignalLostEvent(UUID tripId, UUID customerId, UUID driverId, Instant lastKnownAt) {
}
