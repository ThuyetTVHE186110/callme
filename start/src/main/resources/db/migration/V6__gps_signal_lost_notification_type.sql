-- CLAUDE.md C.7 — "mất tín hiệu GPS / kết nối mạng giữa hành trình" now raises a
-- GPS_SIGNAL_LOST notification to both trip participants (and, via the same event,
-- gives support a timely heads-up) once the driver's GPS trail goes cold mid-trip.

alter table notifications
    drop constraint notifications_type_check;
alter table notifications
    add constraint notifications_type_check
        check (type in ('BOOKING_CONFIRMED', 'TRIP_COMPLETED', 'SOS_RAISED', 'GPS_SIGNAL_LOST'));
