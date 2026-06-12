package com.callme.trip.dto;

import com.callme.trip.entity.IncidentInvestigationStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentReportResponse(UUID id, UUID tripId, UUID reportedByProfileId, UUID customerId, UUID driverId,
                                      String description, IncidentInvestigationStatus investigationStatus,
                                      Instant reportedAt, String resolutionNote, Instant resolvedAt) {
}
