package com.callme.common.port.dto;

import java.util.UUID;

/** Snapshot of who was on a trip and whether it actually finished — used to validate ratings (CLAUDE.md F.3: only real participants of a completed trip may rate one another). */
public record TripParticipants(UUID customerId, UUID driverId, boolean completed) {
}
