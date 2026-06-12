package com.callme.driver.dto;

import com.callme.driver.entity.BackgroundCheckStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * CLAUDE.md §4.5/§4.2 — admin-submitted result of a periodic reverification round
 * (background check, driving license, liability insurance — checked together, as a
 * real reverification round would be).
 */
public record VerifyDriverRequest(
        @NotNull BackgroundCheckStatus backgroundCheckStatus,
        @NotNull @Future LocalDate licenseExpiryDate,
        @NotNull @Future LocalDate insuranceValidUntil) {
}
