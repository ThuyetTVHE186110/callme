package com.callme.trip.dto;

import java.math.BigDecimal;

/**
 * CLAUDE.md C.5/A.4 — the re-quoted fare returned synchronously to the customer who
 * just changed the destination, so the current price is in their hands before it is
 * ever locked in at completion (the doc's "khách luôn biết giá hiện hành" promise —
 * previously this quote was computed and then only written to the server log).
 */
public record DestinationChangeResponse(BigDecimal requotedFareAmount, String requotedFareCurrency, double distanceKm) {
}
