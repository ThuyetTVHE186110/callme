package com.callme.trip.dto;

import com.callme.common.shared.CancellationReason;
import com.callme.trip.entity.TripStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record TripResponse(UUID id,
                            UUID bookingId,
                            UUID customerId,
                            UUID driverId,
                            TripStatus status,
                            BigDecimal finalFareAmount,
                            String finalFareCurrency,
                            CancellationReason cancellationReason) {
}
