package com.callme.common.event;

import com.callme.common.shared.GeoPoint;
import com.callme.common.shared.Money;

import java.util.UUID;

/**
 * CLAUDE.md C.5/A.4 — the riding customer redirected the trip and the fare was
 * re-quoted on the spot. Published by the trip module; consumed by notification to
 * tell BOTH parties the new destination and current price ("khách luôn biết giá
 * hiện hành trước khi nó chốt" — and the driver deserves the same transparency about
 * the route and money of the job they're mid-way through). The caller additionally
 * receives the quote synchronously in the API response.
 */
public record TripDestinationChangedEvent(UUID tripId, UUID customerId, UUID driverId,
                                          GeoPoint newDestination, Money newFareEstimate) {
}
