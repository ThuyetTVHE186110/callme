package com.callme.notification.entity;

public enum NotificationType {
    BOOKING_CONFIRMED,
    TRIP_COMPLETED,
    SOS_RAISED,
    /** CLAUDE.md C.7 — driver's GPS trail went cold mid-trip; both participants and support are alerted. */
    GPS_SIGNAL_LOST,
    /** CLAUDE.md E.2 — driver ended an IN_PROGRESS trip after declaring a safe drop-off; both participants and support are alerted. */
    TRIP_ABORTED_MIDWAY
}
