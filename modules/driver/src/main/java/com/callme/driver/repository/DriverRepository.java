package com.callme.driver.repository;

import com.callme.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByOnlineTrue();

    /** Candidates eligible for matching — online AND not already committed to another trip (CLAUDE.md line 59 / B.1). */
    List<Driver> findByOnlineTrueAndOnTripFalse();

    /**
     * CLAUDE.md G.4 — a coarse lat/lng bounding-box pre-filter pushed down to the
     * database (backed by the index on `drivers`, see {@link Driver}) so matching
     * doesn't have to load and compute great-circle distance for every online driver
     * citywide — only those in the rough neighbourhood of the pickup point. The
     * caller still applies the precise {@code GeoPoint.distanceKm} circle check on
     * top, since a bounding box is a square (it over-includes the corners). This is
     * an incremental step toward real geo-indexing (PostGIS/geohash) without requiring
     * a database engine change — at very large driver-pool scale that is still the
     * correct long-term answer.
     */
    List<Driver> findByOnlineTrueAndOnTripFalseAndLastKnownLatitudeBetweenAndLastKnownLongitudeBetween(
            double minLatitude, double maxLatitude, double minLongitude, double maxLongitude);
}
