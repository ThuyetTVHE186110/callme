package com.callme.driver.dto;

import com.callme.driver.entity.BackgroundCheckStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(UUID id, String displayName, boolean online,
                              BackgroundCheckStatus backgroundCheckStatus,
                              LocalDate licenseExpiryDate,
                              LocalDate insuranceValidUntil,
                              Instant lastReverificationAt,
                              boolean eligibleToGoOnline) {
}
