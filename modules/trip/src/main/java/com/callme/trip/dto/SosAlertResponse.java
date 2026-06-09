package com.callme.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record SosAlertResponse(UUID id, UUID tripId, UUID raisedByProfileId, String note, Instant raisedAt) {
}
