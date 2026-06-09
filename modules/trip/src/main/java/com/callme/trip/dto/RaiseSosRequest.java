package com.callme.trip.dto;

import jakarta.validation.constraints.Size;

public record RaiseSosRequest(@Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự") String note) {
}
