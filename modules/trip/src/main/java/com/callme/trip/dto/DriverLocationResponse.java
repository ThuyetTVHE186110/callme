package com.callme.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(UUID driverId, double latitude, double longitude, Instant updatedAt) {
}
