package com.callme.payment.dto;

import com.callme.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(@NotNull(message = "Phương thức thanh toán không được để trống") PaymentMethod method) {
}
