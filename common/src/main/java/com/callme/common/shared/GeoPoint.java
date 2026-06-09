package com.callme.common.shared;

public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("invalid latitude: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("invalid longitude: " + longitude);
        }
    }

    /**
     * Equirectangular approximation — good enough for short, same-city "drive me
     * home" radii. Centralised here so every module that needs "how far apart are
     * these two points" (availability filtering, matching ranking, fare estimation...)
     * agrees on the same answer instead of each re-deriving its own formula.
     */
    public double distanceKm(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLng = Math.toRadians(other.longitude - this.longitude);
        double meanLat = Math.toRadians((other.latitude + this.latitude) / 2);
        double x = dLng * Math.cos(meanLat);
        return Math.sqrt(x * x + dLat * dLat) * EARTH_RADIUS_KM;
    }
}
