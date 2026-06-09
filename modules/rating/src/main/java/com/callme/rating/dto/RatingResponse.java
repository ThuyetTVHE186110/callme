package com.callme.rating.dto;

import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID id, UUID tripId, UUID raterUserId, UUID rateeUserId,
                              int score, String comment, boolean complaint,
                              Instant createdAt, Instant updatedAt) {
}
