package com.callme.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record RouteDeviationFlagResponse(UUID id, UUID tripId, UUID driverId,
                                          double expectedDistanceKm, double actualDistanceKm,
                                          double deviationRatio, Instant flaggedAt) {
}
