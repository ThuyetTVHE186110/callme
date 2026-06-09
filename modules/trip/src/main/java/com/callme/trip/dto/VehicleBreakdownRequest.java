package com.callme.trip.dto;

import jakarta.validation.constraints.Size;

/** CLAUDE.md C.3 — driver-supplied note on what failed (won't start, flat tyre, overheating...), kept for the support handoff (cứu hộ / đổi sang taxi thường). */
public record VehicleBreakdownRequest(@Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự") String note) {
}
