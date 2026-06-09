package com.callme.location.service.impl;

import com.callme.common.port.DriverRouteTracePort;
import com.callme.common.shared.GeoPoint;
import com.callme.location.repository.LocationUpdateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md C.8 — replays the driver's recorded GPS pings between two instants
 * and sums the consecutive-point distances into a real-world travelled distance.
 * Deliberately a plain summation over whatever pings exist (no interpolation, no
 * smoothing): sparse pings under-count the true distance, which only makes this
 * heuristic conservative — it never *invents* distance the driver didn't report.
 */
@Service
public class DriverRouteTracePortImpl implements DriverRouteTracePort {

    private final LocationUpdateRepository locationUpdateRepository;

    public DriverRouteTracePortImpl(LocationUpdateRepository locationUpdateRepository) {
        this.locationUpdateRepository = locationUpdateRepository;
    }

    @Override
    public double cumulativeDistanceKm(UUID driverId, Instant from, Instant to) {
        List<com.callme.location.entity.LocationUpdate> trail =
                locationUpdateRepository.findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(driverId, from, to);

        double total = 0.0;
        for (int i = 1; i < trail.size(); i++) {
            var previous = new GeoPoint(trail.get(i - 1).getLatitude(), trail.get(i - 1).getLongitude());
            var current = new GeoPoint(trail.get(i).getLatitude(), trail.get(i).getLongitude());
            total += previous.distanceKm(current);
        }
        return total;
    }
}
