package com.callme.common.port.dto;

import java.time.Instant;

/** A trip participant's (driver or customer) last known GPS fix. */
public record LocationSnapshot(double latitude, double longitude, Instant updatedAt) {
}
