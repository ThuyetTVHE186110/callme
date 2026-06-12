package com.callme.payment.dto;

import com.callme.payment.entity.PaymentMethod;
import com.callme.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID tripId, UUID customerId, UUID driverId,
                               BigDecimal amount, String currency,
                               PaymentMethod method, PaymentStatus status) {
}
