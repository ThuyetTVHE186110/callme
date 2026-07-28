-- Lets the assigned driver see the customer's last known GPS fix (e.g. while heading
-- to or waiting at pickup) — the customer-side symmetric counterpart to the driver
-- location tracking already on `drivers` (last_known_latitude/longitude/updated_at).
-- Nullable/defaulted because, unlike a driver's app, a customer is not expected to
-- push GPS as a rule; last_location_updated_at stays null until the first report.
alter table customers
    add column last_known_latitude  float8 not null default 0;
alter table customers
    add column last_known_longitude float8 not null default 0;
alter table customers
    add column last_location_updated_at timestamp(6) with time zone;

create table customer_location_updates (
    id              uuid not null,
    customer_id     uuid,
    latitude        float8 not null,
    longitude       float8 not null,
    recorded_at     timestamp(6) with time zone,
    primary key (id)
);
-- Mirrors idx_location_updates_driver_recorded_at — same dispute-resolution evidence
-- trail (CLAUDE.md D.2), customer side.
create index idx_customer_location_updates_customer_recorded_at on customer_location_updates (customer_id, recorded_at desc);
