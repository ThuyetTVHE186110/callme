package com.callme.common.port;

import com.callme.common.port.dto.TripParticipants;

import java.util.Optional;
import java.util.UUID;

/**
 * Published by the trip module; consumed by rating so it can verify a rating is being
 * submitted by an actual participant of an actually-completed trip, about the other
 * actual participant — without trusting raterUserId/rateeUserId values from the client
 * (CLAUDE.md edge case F.3 — anti-abuse for ratings).
 */
public interface TripParticipantsPort {

    Optional<TripParticipants> findParticipants(UUID tripId);
}
