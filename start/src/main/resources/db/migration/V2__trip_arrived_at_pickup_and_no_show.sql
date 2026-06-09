-- CLAUDE.md §5 — splits Trip's "STARTED" into "đang đến điểm đón" (STARTED) and
-- "đã đến nơi, chưa cầm lái" (ARRIVED_AT_PICKUP), the structural prerequisite this
-- document calls out before no-show / waiting-fee / ID-verification (group C) can
-- be modeled correctly. Also adds CUSTOMER_NO_SHOW (CLAUDE.md C.1) as a distinct
-- cancellation reason, separate from an ordinary CUSTOMER_REQUEST cancel, so a fee
-- policy can tell "khách đổi ý" apart from "khách không xuất hiện".

alter table trips
    add column arrived_at_pickup_at timestamp(6) with time zone;

alter table trips
    drop constraint trips_status_check;
alter table trips
    add constraint trips_status_check
        check (status in ('STARTED', 'ARRIVED_AT_PICKUP', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

alter table trips
    drop constraint trips_cancellation_reason_check;
alter table trips
    add constraint trips_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW'));

alter table bookings
    drop constraint bookings_cancellation_reason_check;
alter table bookings
    add constraint bookings_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW'));
