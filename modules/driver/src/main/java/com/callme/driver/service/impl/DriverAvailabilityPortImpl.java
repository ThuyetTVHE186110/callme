package com.callme.driver.service.impl;

import com.callme.common.port.DriverAvailabilityPort;
import com.callme.common.port.dto.DriverSummary;
import com.callme.common.shared.GeoPoint;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Implements the cross-module contract published in `common`. Other modules (e.g. booking)
 * depend only on {@link DriverAvailabilityPort}; Spring wires this implementation in at
 * runtime once `start` aggregates every module on the classpath.
 */
@Service
public class DriverAvailabilityPortImpl implements DriverAvailabilityPort {

    /**
     * CLAUDE.md B.2 — beyond this age a driver's last GPS fix is considered stale
     * ("tài xế ảo": online in the DB but effectively unreachable). Excluding them
     * here means dispatch never even sees — let alone reserves — a ghost candidate.
     */
    private static final Duration LOCATION_FRESHNESS_WINDOW = Duration.ofMinutes(5);

    /** Rough km-per-degree-of-latitude (and, at the equator, of longitude too) used for the G.4 bounding-box pre-filter. */
    private static final double KM_PER_DEGREE_LATITUDE = 111.0;

    private final DriverRepository driverRepository;

    public DriverAvailabilityPortImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public List<DriverSummary> findAvailableNear(GeoPoint location, double radiusKm) {
        var now = Instant.now();
        return findCandidatesNear(location, radiusKm).stream()
                .filter(driver -> driver.hasFreshLocation(now, LOCATION_FRESHNESS_WINDOW))
                .map(this::toSummary)
                // The bounding box above is a square — it over-includes the corners.
                // `radiusKm` must still mean an actual circle, or B.5's radius expansion
                // (and "near" itself) becomes meaningless.
                .filter(summary -> location.distanceKm(summary.lastKnownLocation()) <= radiusKm)
                .toList();
    }

    /**
     * CLAUDE.md G.4 — pushes a coarse lat/lng bounding box down to the database so we
     * don't load and distance-check every online driver citywide, only those in the
     * rough neighbourhood of the pickup point. Longitude degrees shrink toward the
     * poles (cos(latitude)), so its span is widened accordingly; both spans are
     * clamped to valid coordinate ranges since a wide `radiusKm` near a pole or the
     * antimeridian could otherwise overflow ±90°/±180°.
     */
    private List<Driver> findCandidatesNear(GeoPoint location, double radiusKm) {
        double latSpan = radiusKm / KM_PER_DEGREE_LATITUDE;
        double lngDegreeKm = Math.max(KM_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(location.latitude())), 0.001);
        double lngSpan = radiusKm / lngDegreeKm;

        double minLat = Math.max(location.latitude() - latSpan, -90.0);
        double maxLat = Math.min(location.latitude() + latSpan, 90.0);
        double minLng = Math.max(location.longitude() - lngSpan, -180.0);
        double maxLng = Math.min(location.longitude() + lngSpan, 180.0);

        return driverRepository.findByOnlineTrueAndOnTripFalseAndLastKnownLatitudeBetweenAndLastKnownLongitudeBetween(
                minLat, maxLat, minLng, maxLng);
    }

    private DriverSummary toSummary(Driver driver) {
        var location = new GeoPoint(driver.getLastKnownLatitude(), driver.getLastKnownLongitude());
        return new DriverSummary(driver.getId(), driver.getDisplayName(), location, driver.getNoResponseStrikes());
    }
}
