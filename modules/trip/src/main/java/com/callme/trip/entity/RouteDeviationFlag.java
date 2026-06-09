package com.callme.trip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md C.8 — "tài xế cố tình đi đường vòng để tăng cước". A flat, append-only
 * record raised automatically at trip completion when the driver's GPS-traced
 * distance significantly exceeds the straight-line distance the fare was charged
 * on (see {@code TripServiceImpl.checkRouteDeviation}). Deliberately NOT a
 * fraud verdict — sparse GPS, real detours around road closures/traffic, and
 * legitimate multi-stop favours all produce the same signal. It is a worklist
 * entry for support to investigate with the full GPS trail in hand, mirroring
 * how {@link SosAlert} hands emergencies to support rather than auto-acting on them.
 */
@Entity
@Table(name = "route_deviation_flags")
@Getter
@NoArgsConstructor(force = true)
public class RouteDeviationFlag {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;

    private UUID driverId;

    private double expectedDistanceKm;

    private double actualDistanceKm;

    private double deviationRatio;

    private Instant flaggedAt;

    public RouteDeviationFlag(UUID tripId, UUID driverId, double expectedDistanceKm, double actualDistanceKm, double deviationRatio, Instant flaggedAt) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.expectedDistanceKm = expectedDistanceKm;
        this.actualDistanceKm = actualDistanceKm;
        this.deviationRatio = deviationRatio;
        this.flaggedAt = flaggedAt;
    }
}
