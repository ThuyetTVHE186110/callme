package com.callme.rating.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewPatternFlagResponse(UUID id, UUID raterUserId, UUID rateeUserId,
                                         long lowScoreCount, Instant flaggedAt) {
}
