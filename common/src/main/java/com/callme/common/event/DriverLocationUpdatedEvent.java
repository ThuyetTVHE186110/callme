package com.callme.common.event;

import com.callme.common.shared.GeoPoint;

import java.util.UUID;

/**
 * Published by the location module whenever a driver reports a new GPS fix;
 * consumed by the driver module to refresh the "last known location" used for matching.
 */
public record DriverLocationUpdatedEvent(UUID driverId, GeoPoint location) {
}
