package com.callme.common.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by the location module; consumed by trip to reconstruct how far a
 * driver actually travelled during a trip — CLAUDE.md C.8 ("tài xế cố tình đi
 * đường vòng để tăng cước"). Sums consecutive GPS pings recorded for the driver
 * within {@code [from, to]}, giving a real-world travelled distance that can be
 * compared against the straight-line distance the fare was charged on.
 */
public interface DriverRouteTracePort {

    double cumulativeDistanceKm(UUID driverId, Instant from, Instant to);
}
