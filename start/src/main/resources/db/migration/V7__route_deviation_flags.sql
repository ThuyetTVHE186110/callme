-- CLAUDE.md C.8 — "tài xế cố tình đi đường vòng để tăng cước". Auto-raised at trip
-- completion (TripServiceImpl.checkRouteDeviation) when the driver's GPS-traced
-- distance significantly exceeds the straight-line distance the fare was charged
-- on; queued here as a flat worklist for support to review, mirroring sos_alerts.

create table route_deviation_flags (
    id                      uuid not null,
    trip_id                 uuid,
    driver_id               uuid,
    expected_distance_km    float8 not null,
    actual_distance_km      float8 not null,
    deviation_ratio         float8 not null,
    flagged_at              timestamp(6) with time zone,
    primary key (id)
);
-- RouteDeviationFlagRepository.findAllByOrderByFlaggedAtDesc — CSKH worklist sorted by recency.
create index idx_route_deviation_flags_flagged_at on route_deviation_flags (flagged_at desc);

-- DriverRouteTracePortImpl replays a driver's GPS trail within a time window —
-- the existing idx_location_updates_driver_recorded_at (driver_id, recorded_at desc)
-- already covers this access pattern, no new index needed on location_updates.
