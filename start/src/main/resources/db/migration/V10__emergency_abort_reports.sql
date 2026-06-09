-- CLAUDE.md E.2 — "tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS... cần quy
-- trình đặc biệt: tài xế phải đưa xe + khách đến nơi an toàn trước khi được phép kết
-- thúc bất thường, có thể cần điều phối tài xế thay thế đến tiếp ứng tại chỗ". The
-- driver may no longer use the ordinary cancel to bail mid-route — only the dedicated
-- abort flow, which raises one of these flat worklist rows for CSKH (mirrors sos_alerts).

create table emergency_abort_reports (
    id                      uuid not null,
    trip_id                 uuid,
    driver_id               uuid,
    customer_id             uuid,
    safe_location_latitude  double precision not null,
    safe_location_longitude double precision not null,
    note                    varchar(500),
    reported_at             timestamp(6) with time zone,
    primary key (id)
);
-- EmergencyAbortReportRepository.findAllByOrderByReportedAtDesc — CSKH worklist sorted by recency.
create index idx_emergency_abort_reports_reported_at on emergency_abort_reports (reported_at desc);

alter table trips
    drop constraint trips_cancellation_reason_check;
alter table trips
    add constraint trips_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE', 'VEHICLE_BREAKDOWN', 'DRIVER_EMERGENCY_ABORT'));

alter table bookings
    drop constraint bookings_cancellation_reason_check;
alter table bookings
    add constraint bookings_cancellation_reason_check
        check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE', 'CUSTOMER_NO_SHOW', 'DRIVER_UNRESPONSIVE', 'VEHICLE_BREAKDOWN', 'DRIVER_EMERGENCY_ABORT'));

alter table notifications
    drop constraint notifications_type_check;
alter table notifications
    add constraint notifications_type_check
        check (type in ('BOOKING_CONFIRMED', 'TRIP_COMPLETED', 'SOS_RAISED', 'GPS_SIGNAL_LOST', 'TRIP_ABORTED_MIDWAY'));
