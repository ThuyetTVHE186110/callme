package com.callme.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * customerId is intentionally absent — the requesting customer is always derived
 * from the authenticated principal (see BookingController), never trusted from the body.
 */
public record CreateBookingRequest(
        @DecimalMin(value = "-90.0", message = "Vĩ độ đón không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ đón không hợp lệ") double pickupLatitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ đón không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ đón không hợp lệ") double pickupLongitude,
        @DecimalMin(value = "-90.0", message = "Vĩ độ điểm đến không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ điểm đến không hợp lệ") double destinationLatitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ điểm đến không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ điểm đến không hợp lệ") double destinationLongitude) {
}
