package com.callme.common.event;

import com.callme.common.shared.Money;

import java.util.UUID;

/**
 * Published by the trip module once a "drive me home" trip has finished;
 * consumed by payment, rating and notification without depending on trip's internals.
 */
public record TripCompletedEvent(UUID tripId, UUID bookingId, UUID customerId, UUID driverId, Money finalFare) {
}
