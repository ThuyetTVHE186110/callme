package com.callme.trip.dto;

import com.callme.trip.entity.IncidentInvestigationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
        @NotNull(message = "Phải cung cấp kết luận điều tra") IncidentInvestigationStatus status,
        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự") String resolutionNote) {
}
