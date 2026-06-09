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

    Optional<UUID> matchDriver(GeoPoint pickup, double radiusKm);
}
