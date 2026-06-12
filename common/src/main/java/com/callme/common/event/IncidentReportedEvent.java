package com.callme.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md §4.2 — a participant reported a collision/incident involving the
 * customer's vehicle while a driver was behind the wheel (or just after). Carries
 * both profile ids so notification can reach whichever party didn't file the report,
 * and CSKH's worklist (see {@code IncidentReport}) is where the liability
 * investigation (CLAUDE.md §4.2 — three-layer responsibility) actually happens.
 */
public record IncidentReportedEvent(UUID reportId, UUID tripId, UUID reportedByProfileId,
                                     UUID customerId, UUID driverId, Instant reportedAt) {
}
