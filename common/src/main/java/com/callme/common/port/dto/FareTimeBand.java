package com.callme.common.port.dto;

/**
 * CLAUDE.md §4.4 — which time-of-day pricing band a fare was quoted under. Each band
 * carries its own all-inclusive base fare + included distance (mirrors how Chinese
 * designated-driver platforms such as 滴滴代驾/e代驾 price rides: a higher starting
 * price late at night, rather than a flat multiplier layered on a single daytime
 * base). Surfaced on {@link FareBreakdown} so a dispute (CLAUDE.md D.2) shows
 * *which* band's price applied, not just an opaque surcharge line.
 */
public enum FareTimeBand {
    /** 06:00–18:59. */
    DAY,
    /** 19:00–22:59. */
    EVENING,
    /** 23:00–23:59. */
    LATE_NIGHT,
    /** 00:00–05:59 — the deepest, scarcest-driver hours; smallest included distance. */
    OVERNIGHT
}
