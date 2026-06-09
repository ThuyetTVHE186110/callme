package com.callme.trip.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * CLAUDE.md C.2 — the driver must explicitly attest they checked the customer is
 * the registered owner before taking the wheel; {@code @AssertTrue} rejects the
 * request outright at the API boundary if that box isn't checked, before it ever
 * reaches the (equally strict) domain guard in {@code Trip.pickUpCustomer}.
 */
public record PickUpCustomerRequest(
        @AssertTrue(message = "Phải xác nhận đã kiểm tra khách hàng đúng là chủ xe trước khi nhận xe")
        boolean identityVerified) {
}
