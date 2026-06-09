package com.callme.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record ReportLocationRequest(
        @DecimalMin(value = "-90.0", message = "Vĩ độ không hợp lệ") @DecimalMax(value = "90.0", message = "Vĩ độ không hợp lệ") double latitude,
        @DecimalMin(value = "-180.0", message = "Kinh độ không hợp lệ") @DecimalMax(value = "180.0", message = "Kinh độ không hợp lệ") double longitude) {
}
