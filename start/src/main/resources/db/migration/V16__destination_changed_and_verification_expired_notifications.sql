-- CLAUDE.md C.5/A.4 — DESTINATION_CHANGED: the mid-route re-quote now reaches both
-- parties as a notification instead of dying in the server log.
-- CLAUDE.md §4.5 — VERIFICATION_EXPIRED: a driver forced offline by the verification
-- expiry sweep is told why, and what to renew.
alter table notifications
    drop constraint notifications_type_check;
alter table notifications
    add constraint notifications_type_check
        check (type in ('BOOKING_CONFIRMED', 'TRIP_COMPLETED', 'SOS_RAISED', 'GPS_SIGNAL_LOST',
                        'TRIP_ABORTED_MIDWAY', 'INCIDENT_REPORTED', 'DESTINATION_CHANGED', 'VERIFICATION_EXPIRED'));
