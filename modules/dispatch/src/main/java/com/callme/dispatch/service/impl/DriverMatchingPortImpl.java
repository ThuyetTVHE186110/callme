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

    /**
     * CLAUDE.md B.3 — each no-response strike handicaps a candidate by this many
     * kilometres of *effective* distance, instead of strikes being an absolute sort
     * key. An absolute sort meant one strike permanently lost to every 0-strike
     * driver however far away (10 km vs 200 m) — "hạ điểm ưu tiên" hardened into a
     * de-facto lifetime ban, since strikes never decay. As a distance handicap, a
     * lightly-struck nearby driver still wins against a clean faraway one, while the
     * chronically unresponsive sink out of practical contention exactly as intended.
     */
    private static final double STRIKE_DISTANCE_PENALTY_KM = 2.0;

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
    public Optional<UUID> matchDriver(GeoPoint pickup, double radiusKm, UUID excludedDriverId) {
        double radius = radiusKm;
        for (int attempt = 0; attempt <= MAX_EXPANSIONS; attempt++) {
            var reserved = driverAvailabilityPort.findAvailableNear(pickup, radius).stream()
                    // CLAUDE.md B.3/B.4 — never hand this job back to the driver who just
                    // withdrew from it; they're back in the pool and often still the
                    // nearest candidate. Applies across every radius expansion.
                    .filter(candidate -> !candidate.driverId().equals(excludedDriverId))
                    // CLAUDE.md B.3 — rank by effective distance: actual distance plus a
                    // per-strike handicap ("hạ điểm ưu tiên hiển thị"), so unresponsive
                    // history costs ground without becoming a lifetime ban.
                    .sorted(Comparator.comparingDouble((DriverSummary candidate) ->
                            pickup.distanceKm(candidate.lastKnownLocation())
                                    + candidate.noResponseStrikes() * STRIKE_DISTANCE_PENALTY_KM))
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
