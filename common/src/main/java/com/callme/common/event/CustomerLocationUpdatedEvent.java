package com.callme.common.event;

import com.callme.common.shared.GeoPoint;

import java.util.UUID;

/**
 * Published by the location module whenever a customer reports a new GPS fix;
 * consumed by the identity module to refresh the "last known location" exposed
 * to the assigned driver via {@link com.callme.common.port.CustomerLocationPort}.
 */
public record CustomerLocationUpdatedEvent(UUID customerId, GeoPoint location) {
}
