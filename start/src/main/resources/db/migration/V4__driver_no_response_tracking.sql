-- CLAUDE.md B.3 — adapted to this system's atomic-reservation matching: there is no
-- broadcast-and-await-accept window, so the equivalent failure is a matched driver
-- who never sets off toward pickup. `trips.created_at` anchors the periodic sweep
-- that detects this; `drivers.no_response_strikes` records it so the driver sinks in
-- future matching priority ("hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau"),
-- and DRIVER_UNRESPONSIVE distinguishes the resulting cancellation from an ordinary
-- DRIVER_REQUEST so a fee policy never charges the customer for it.

alter table trips
    add column created_at timestamp(6) with time zone;

alter table drivers
    add column no_response_strikes integer not null default 0;

alter table trips
    drop constraint trips_cancellation_reason_check;
alter table trips
    add constraint trips_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE'));

alter table bookings
    drop constraint bookings_cancellation_reason_check;
alter table bookings
    add constraint bookings_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE'));
