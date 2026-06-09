package com.callme.common.event;

import com.callme.common.shared.GeoPoint;

import java.util.UUID;

/**
 * Published by the booking module once a driver has been assigned;
 * consumed by trip (and later notification) without depending on booking's internals.
 *
 * Carries pickup/destination so trip can run its own lifecycle (and compute the
 * final fare on completion via FareEstimationPort) without depending on booking's storage.
 */
public record BookingConfirmedEvent(UUID bookingId, UUID customerId, UUID driverId, GeoPoint pickup, GeoPoint destination) {
}
