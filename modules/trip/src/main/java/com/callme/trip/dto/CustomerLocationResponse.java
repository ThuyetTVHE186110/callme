package com.callme.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerLocationResponse(UUID customerId, double latitude, double longitude, Instant updatedAt) {
}
