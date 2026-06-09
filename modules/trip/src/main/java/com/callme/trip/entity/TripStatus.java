package com.callme.trip.entity;

public enum TripStatus {
    /** Driver assigned, en route to the customer's pickup point — "đang đến điểm đón". */
    STARTED,
    /**
     * CLAUDE.md §5 — driver physically at the pickup point but has not yet taken the
     * wheel: verifying the customer is the registered owner, checking the vehicle,
     * waiting for a still-getting-ready customer (CLAUDE.md C.1, C.2). Distinct from
     * {@link #STARTED} so a no-show timer/waiting fee can anchor on "tài xế đã đến nơi",
     * not "tài xế đã nhận chuyến".
     */
    ARRIVED_AT_PICKUP,
    /** Driver has the wheel of the customer's car — "đang lái xe khách đến điểm đến". */
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
