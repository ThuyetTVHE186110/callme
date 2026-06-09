package com.callme.common.port.dto;

import com.callme.common.shared.GeoPoint;

import java.util.UUID;

/**
 * {@code noResponseStrikes} — CLAUDE.md B.3 — carried through so {@code DriverMatchingPort}
 * can de-prioritize chronically unresponsive drivers without re-querying the driver module.
 */
public record DriverSummary(UUID driverId, String displayName, GeoPoint lastKnownLocation, int noResponseStrikes) {
}
