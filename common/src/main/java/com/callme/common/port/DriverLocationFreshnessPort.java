package com.callme.common.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Published by the driver module; consumed by trip's GPS-loss sweep (CLAUDE.md C.7)
 * so it can tell whether a driver mid-trip has gone dark, without depending on
 * driver's entity internals.
 */
public interface DriverLocationFreshnessPort {

    /** The instant of this driver's last reported GPS fix, or empty if they have never reported one. */
    Optional<Instant> lastReportedAt(UUID driverId);
}
