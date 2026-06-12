package com.callme.notification.entity;

public enum NotificationType {
    BOOKING_CONFIRMED,
    TRIP_COMPLETED,
    SOS_RAISED,
    /** CLAUDE.md C.7 — driver's GPS trail went cold mid-trip; both participants and support are alerted. */
    GPS_SIGNAL_LOST,
    /** CLAUDE.md E.2 — driver ended an IN_PROGRESS trip after declaring a safe drop-off; both participants and support are alerted. */
    TRIP_ABORTED_MIDWAY,
    /** CLAUDE.md §4.2 — a collision/incident was reported on a trip; both participants and support are alerted for the liability investigation. */
    INCIDENT_REPORTED,
    /** CLAUDE.md C.5/A.4 — the customer redirected the trip mid-route; both parties are told the new destination's re-quoted fare. */
    DESTINATION_CHANGED,
    /** CLAUDE.md §4.5 — a verification (license/insurance/background/reverification) lapsed while the driver was online; they were forced offline and told what to renew. */
    VERIFICATION_EXPIRED
}
