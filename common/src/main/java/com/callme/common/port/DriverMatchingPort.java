package com.callme.common.port;

import com.callme.common.shared.GeoPoint;

import java.util.Optional;
import java.util.UUID;

/**
 * Published by the dispatch module; consumed by booking so that the matching
 * strategy (nearest-first today, smarter ranking later) stays its own bounded
 * context instead of leaking into booking's request-handling logic.
 */
public interface DriverMatchingPort {

    /**
     * Finds and atomically reserves the best available driver near {@code pickup}.
     *
     * @param excludedDriverId optional (nullable) — a driver who must not be offered
     *                         this particular job even if they're the best candidate.
     *                         Used by the B.4 re-dispatch flow: the driver who just
     *                         withdrew from (or went unresponsive on) this very booking
     *                         was released back into the pool moments earlier and is
     *                         usually still the nearest candidate — without the
     *                         exclusion they'd be matched straight back to the customer
     *                         they abandoned (looping forever in the unresponsive case).
     *                         They remain matchable for other requests.
     */
    Optional<UUID> matchDriver(GeoPoint pickup, double radiusKm, UUID excludedDriverId);
}
