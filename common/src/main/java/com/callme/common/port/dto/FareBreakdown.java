package com.callme.common.port.dto;

import com.callme.common.shared.Money;

/**
 * CLAUDE.md §4.4 — itemized surcharges, not just a single number: showing the
 * customer exactly what they're being charged for (and why) is what keeps a
 * 2 a.m. fare dispute (CLAUDE.md D.2) a one-glance lookup instead of a CSKH escalation.
 */
public record FareBreakdown(Money baseFare,
                             Money distanceCharge,
                             Money nightSurcharge,
                             Money remoteAreaSurcharge,
                             Money total) {
}
