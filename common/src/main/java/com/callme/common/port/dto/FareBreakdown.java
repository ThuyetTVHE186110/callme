package com.callme.common.port.dto;

import com.callme.common.shared.Money;

/**
 * CLAUDE.md §4.4 — itemized charges, not just a single number: showing the
 * customer exactly what they're being charged for (and why) is what keeps a
 * 2 a.m. fare dispute (CLAUDE.md D.2) a one-glance lookup instead of a CSKH escalation.
 *
 * {@code baseFare} already includes {@code includedKm} of travel — {@code timeBand}
 * records which time-of-day band set that base fare and included distance (CLAUDE.md
 * §4.4's time-banded pricing), so the "night surcharge" is simply visible as a
 * higher base fare for a later band rather than a separate multiplier line.
 *
 * {@code waitingFee} is "phí chờ khách" (CLAUDE.md §4.4) — zero for an up-front
 * estimate (no driver has arrived yet), populated at completion time from how long
 * the driver waited at the pickup point before taking the wheel.
 */
public record FareBreakdown(FareTimeBand timeBand,
                             Money baseFare,
                             int includedKm,
                             Money distanceCharge,
                             Money remoteAreaSurcharge,
                             Money waitingFee,
                             Money total) {
}
