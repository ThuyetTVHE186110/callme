package com.callme.common.port;

import com.callme.common.port.dto.FareQuote;
import com.callme.common.shared.GeoPoint;

import java.time.Instant;

/**
 * Published by the pricing module; consumed by booking to quote a fare up front
 * and by trip to compute the final fare once the destination is known.
 *
 * {@code atTime} drives the night-time surcharge (CLAUDE.md §4.4) — pass the moment
 * the fare actually applies (booking creation time for an estimate, completion time
 * for the final charge), not a fixed instant, so the same trip quoted at 9 p.m. and
 * charged at 1 a.m. reflects the surcharge that actually applied while it ran.
 */
public interface FareEstimationPort {

    FareQuote estimate(GeoPoint pickup, GeoPoint destination, Instant atTime);
}
