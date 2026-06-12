package com.callme.booking.dto;

import com.callme.booking.entity.BookingStatus;
import com.callme.common.shared.CancellationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingResponse(UUID id,
                               UUID customerId,
                               BookingStatus status,
                               UUID assignedDriverId,
                               BigDecimal estimatedFareAmount,
                               String estimatedFareCurrency,
                               CancellationReason cancellationReason,
                               BigDecimal cancellationFeeAmount,
                               String cancellationFeeCurrency,
                               Instant scheduledAt) {
}
