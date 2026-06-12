package com.callme.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.time.Instant;

/**
 * customerId is intentionally absent — the requesting customer is always derived
 * from the authenticated principal (see BookingController), never trusted from the body.
 *
 * {@code scheduledAt} (CLAUDE.md §4.7) is optional — null means "ngay bây giờ" (today's
 * immediate-matching behaviour). When present it's validated against
 * {@link com.callme.booking.entity.Booking#MAX_ADVANCE_BOOKING_WINDOW} in the entity
 * constructor, where it can be checked against the same {@code now} used for the
 * "must be in the future" check.
 */
public record CreateBookingRequest(
        @DecimalMin(value = "-90.0", message = "Vĩ độ đón không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ đón không hợp lệ") double pickupLatitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ đón không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ đón không hợp lệ") double pickupLongitude,
        @DecimalMin(value = "-90.0", message = "Vĩ độ điểm đến không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ điểm đến không hợp lệ") double destinationLatitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ điểm đến không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ điểm đến không hợp lệ") double destinationLongitude,
        Instant scheduledAt) {
}
