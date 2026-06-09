package com.callme.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record EmergencyAbortReportResponse(UUID id, UUID tripId, UUID driverId, UUID customerId,
                                            double safeLatitude, double safeLongitude,
                                            String note, Instant reportedAt) {
}
