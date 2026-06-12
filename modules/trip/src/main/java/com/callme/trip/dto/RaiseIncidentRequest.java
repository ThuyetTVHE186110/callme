package com.callme.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RaiseIncidentRequest(
        @NotBlank(message = "Phải mô tả sự cố")
        @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự") String description) {
}
