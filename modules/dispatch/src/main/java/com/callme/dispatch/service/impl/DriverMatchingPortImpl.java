package com.callme.dispatch.service.impl;

import com.callme.common.port.DriverAvailabilityPort;
import com.callme.common.port.DriverMatchingPort;
import com.callme.common.port.DriverReservationPort;
import com.callme.common.port.dto.DriverSummary;
import com.callme.common.shared.GeoPoint;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the cross-module contract published in `common`. Booking depends only on
 * {@link DriverMatchingPort} — the matching strategy (today: nearest candidate first)
 * lives here so it can evolve (load balancing, driver rating, acceptance-rate weighting...)
 * without booking ever knowing how a driver gets chosen.
 */
@Service
public class DriverMatchingPortImpl implements DriverMatchingPort {

    /**
     * CLAUDE.md B.5 — sparse areas (ngoại thành, giờ khuya muộn) can have zero
     * drivers within the customer's initial radius. Rather than surfacing
     * NO_DRIVER_FOUND immediately, widen the search a couple of times before
     * giving up — trading a slightly longer ETA for an actual match.
     */
    private static final int MAX_EXPANSIONS = 2;
    private static final double EXPANSION_FACTOR = 2.0;

    private final DriverAvailabilityPort driverAvailabilityPort;
    private final DriverReservationPort driverReservationPort;

    public DriverMatchingPortImpl(DriverAvailabilityPort driverAvailabilityPort, DriverReservationPort driverReservationPort) {
        this.driverAvailabilityPort = driverAvailabilityPort;
        this.driverReservationPort = driverReservationPort;
    }

    /**
     * Walks candidates nearest-first and atomically claims the first one still free
     * (CLAUDE.md B.1 — "ai chạm trước thắng"). Reserving here, before booking ever
     * persists a CONFIRMED status, is what prevents two concurrent requests from both
     * walking away believing they got the same driver: a candidate who lost a
     * concurrent race is simply skipped in favour of the next-nearest.
     *
     * If nothing reservable turns up within `radiusKm`, the search radius is doubled
     * up to {@link #MAX_EXPANSIONS} times before reporting "no driver found" (CLAUDE.md B.5).
     */
    @Override
    public Optional<UUID> matchDriver(GeoPoint pickup, double radiusKm) {
        double radius = radiusKm;
        for (int attempt = 0; attempt <= MAX_EXPANSIONS; attempt++) {
            var reserved = driverAvailabilityPort.findAvailableNear(pickup, radius).stream()
                    // CLAUDE.md B.3 — chronically unresponsive drivers ("hạ điểm ưu tiên
                    // hiển thị") sink behind responsive ones at the same rough distance,
                    // rather than being offered the next customer at their expense.
                    .sorted(Comparator.comparingInt(DriverSummary::noResponseStrikes)
                            .thenComparingDouble(candidate -> pickup.distanceKm(candidate.lastKnownLocation())))
                    .map(DriverSummary::driverId)
                    .filter(driverReservationPort::tryReserve)
                    .findFirst();
            if (reserved.isPresent()) {
                return reserved;
            }
            radius *= EXPANSION_FACTOR;
        }
        return Optional.empty();
    }
}
