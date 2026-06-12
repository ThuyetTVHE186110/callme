-- CLAUDE.md §4.2 — "tai nạn / va chạm trong khi tài xế cầm lái xe khách". A flat,
-- append-only worklist of reported incidents (mirrors emergency_abort_reports /
-- sos_alerts), resolved by CSKH into one of the three liability layers.

create table incident_reports (
    id                      uuid not null,
    trip_id                 uuid,
    reported_by_profile_id  uuid,
    customer_id             uuid,
    driver_id               uuid,
    description             varchar(1000),
    investigation_status    varchar(30) not null,
    reported_at             timestamp(6) with time zone,
    resolution_note         varchar(1000),
    resolved_at             timestamp(6) with time zone,
    primary key (id)
);
alter table incident_reports
    add constraint incident_reports_investigation_status_check
        check (investigation_status in ('REPORTED', 'UNDER_INVESTIGATION', 'RESOLVED_NO_FAULT', 'RESOLVED_DRIVER_FAULT', 'RESOLVED_COMPANY_LIABLE'));

-- IncidentReportRepository.findAllByOrderByReportedAtDesc — CSKH worklist sorted by recency.
create index idx_incident_reports_reported_at on incident_reports (reported_at desc);

alter table notifications
    drop constraint notifications_type_check;
alter table notifications
    add constraint notifications_type_check
        check (type in ('BOOKING_CONFIRMED', 'TRIP_COMPLETED', 'SOS_RAISED', 'GPS_SIGNAL_LOST', 'TRIP_ABORTED_MIDWAY', 'INCIDENT_REPORTED'));
