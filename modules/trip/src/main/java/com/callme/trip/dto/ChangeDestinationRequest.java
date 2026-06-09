package com.callme.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record ChangeDestinationRequest(
        @DecimalMin(value = "-90.0", message = "Vĩ độ điểm đến không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ điểm đến không hợp lệ") double latitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ điểm đến không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ điểm đến không hợp lệ") double longitude) {
}
