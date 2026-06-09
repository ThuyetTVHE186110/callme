package com.callme.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md C.6 — either trip participant hit the emergency button. Carries both
 * profile ids (not just the raiser) so listeners — notification, support tooling —
 * can immediately reach the *other* party without re-querying the trip, and act
 * fast: this is the one event in the system where seconds matter.
 */
public record SosRaisedEvent(UUID alertId, UUID tripId, UUID raisedByProfileId,
                              UUID customerId, UUID driverId, Instant raisedAt, String note) {
}
