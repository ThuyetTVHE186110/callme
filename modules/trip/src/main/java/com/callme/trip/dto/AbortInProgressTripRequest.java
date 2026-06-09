package com.callme.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CLAUDE.md E.2 — the safe-location coordinates double as the driver's attestation
 * that they brought the customer's car (and the customer) to safety *before*
 * ending the trip; {@code @NotNull} rejects the request at the API boundary if
 * either coordinate is missing, mirroring how {@link PickUpCustomerRequest} forces
 * the identity-check attestation before the domain guard is even reached.
 */
public record AbortInProgressTripRequest(
        @NotNull(message = "Phải cung cấp vị trí an toàn đã đưa xe và khách đến")
        @DecimalMin(value = "-90.0", message = "Vĩ độ vị trí an toàn không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ vị trí an toàn không hợp lệ") Double safeLatitude,
        @NotNull(message = "Phải cung cấp vị trí an toàn đã đưa xe và khách đến")
        @DecimalMin(value = "-180.0", message = "Kinh độ vị trí an toàn không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ vị trí an toàn không hợp lệ") Double safeLongitude,
        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự") String note) {
}
