package com.callme.common.port;

import com.callme.common.port.dto.DriverSummary;
import com.callme.common.shared.GeoPoint;

import java.util.List;

/**
 * Published by the driver module; consumed by other modules (e.g. booking) via
 * dependency injection so they never depend on driver's internals directly.
 */
public interface DriverAvailabilityPort {

    List<DriverSummary> findAvailableNear(GeoPoint location, double radiusKm);
}
