-- CLAUDE.md C.3 — "xe của khách hỏng hóc / không khởi động được" is squarely the
-- customer's risk, not a fault dispute between participants; VEHICLE_BREAKDOWN gives
-- it a dedicated, fee-neutral audit trail distinct from any *_REQUEST cancellation.

alter table trips
    drop constraint trips_cancellation_reason_check;
alter table trips
    add constraint trips_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE', 'VEHICLE_BREAKDOWN'));

alter table bookings
    drop constraint bookings_cancellation_reason_check;
alter table bookings
    add constraint bookings_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE', 'VEHICLE_BREAKDOWN'));
