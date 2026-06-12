package com.callme.trip.entity;

/**
 * CLAUDE.md §4.2 — the three liability layers a collision/incident can resolve into:
 * the company's per-trip liability cover ({@link #RESOLVED_NO_FAULT} from the
 * driver's perspective — normal operation), the customer's own vehicle insurance
 * ({@link #RESOLVED_DRIVER_FAULT} — driver error found, company covers the excess),
 * or a finding that puts the company itself on the hook ({@link #RESOLVED_COMPANY_LIABLE}).
 * A new report starts at {@link #REPORTED} and may move through
 * {@link #UNDER_INVESTIGATION} before CSKH records one of the {@code RESOLVED_*} outcomes.
 */
public enum IncidentInvestigationStatus {
    REPORTED,
    UNDER_INVESTIGATION,
    RESOLVED_NO_FAULT,
    RESOLVED_DRIVER_FAULT,
    RESOLVED_COMPANY_LIABLE
}
