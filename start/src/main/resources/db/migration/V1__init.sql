-- Baseline schema for the "đặt người lái xe hộ" domain.
--
-- Mirrors exactly what Hibernate generates from the current entities (captured via
-- a `ddl-auto=create` run against a clean database) — `spring.jpa.hibernate.ddl-auto`
-- is `validate` from here on, so this file (and only this file going forward) owns
-- the schema. Modules stay decoupled at the data layer too: every cross-module
-- reference is a bare `uuid` column, not a foreign key — the same boundary the
-- port/adapter contracts enforce in code.
--
-- Indexes beyond Hibernate's own (idx_drivers_availability_location, created
-- declaratively on the Driver entity) are added here based on the actual
-- repository query methods in use — see the comment beside each one.

create table accounts (
    id              uuid not null,
    active          boolean not null,
    password_hash   varchar(255) not null,
    phone_number    varchar(255) not null,
    profile_id      uuid not null,
    role            varchar(255) not null check (role in ('CUSTOMER', 'DRIVER', 'ADMIN')),
    primary key (id),
    unique (phone_number)
);

create table customers (
    id              uuid not null,
    email           varchar(255),
    full_name       varchar(255),
    phone_number    varchar(255),
    primary key (id)
);
-- CustomerRepository.findByPhoneNumber — login/lookup by phone.
create index idx_customers_phone_number on customers (phone_number);

create table drivers (
    id                          uuid not null,
    display_name                varchar(255),
    online                      boolean not null,
    on_trip                     boolean not null,
    last_known_latitude         float8 not null,
    last_known_longitude        float8 not null,
    last_location_updated_at    timestamp(6) with time zone,
    version                     bigint not null,
    primary key (id)
);
-- Declared on the Driver entity (@Table(indexes = ...)) — backs DriverRepository's
-- bounding-box pre-filter for matching (CLAUDE.md G.4). Kept here too so a fresh
-- database matches Hibernate's validate-mode expectations from a single source of truth.
create index idx_drivers_availability_location on drivers (online, on_trip, last_known_latitude, last_known_longitude);

create table bookings (
    id                          uuid not null,
    customer_id                 uuid,
    assigned_driver_id          uuid,
    pickup_latitude             float8 not null,
    pickup_longitude            float8 not null,
    destination_latitude        float8 not null,
    destination_longitude       float8 not null,
    estimated_fare_amount       numeric(38, 2),
    estimated_fare_currency     varchar(255),
    status                      varchar(255) check (status in ('PENDING', 'CONFIRMED', 'NO_DRIVER_FOUND', 'CANCELLED')),
    cancellation_reason         varchar(255) check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE')),
    idempotency_key             varchar(255),
    version                     bigint not null,
    primary key (id),
    unique (customer_id, idempotency_key)
);
-- BookingRepository.existsByCustomerIdAndStatusIn — concurrent-booking guard (CLAUDE.md A.6),
-- runs on every booking creation. The (customer_id, idempotency_key) unique index above
-- leads with customer_id but can't serve an IN(...) on status efficiently on its own.
create index idx_bookings_customer_status on bookings (customer_id, status);

create table trips (
    id                          uuid not null,
    booking_id                  uuid,
    customer_id                 uuid,
    driver_id                   uuid,
    pickup_latitude             float8 not null,
    pickup_longitude            float8 not null,
    destination_latitude        float8 not null,
    destination_longitude       float8 not null,
    status                      varchar(255) check (status in ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    final_fare_amount           numeric(38, 2),
    final_fare_currency         varchar(255),
    cancellation_reason         varchar(255) check (cancellation_reason in ('CUSTOMER_REQUEST', 'DRIVER_REQUEST', 'FORCE_MAJEURE', 'SYSTEM_CASCADE')),
    primary key (id)
);
-- TripRepository.findFirstByBookingIdAndStatusInOrderByIdDesc — the hot lookup for every
-- cancellation cascade and B.4 re-dispatch; a booking can have several historical trip
-- rows (CLAUDE.md B.4), so this must filter by status and order, not just equality.
create index idx_trips_booking_status on trips (booking_id, status, id);

create table sos_alerts (
    id                      uuid not null,
    trip_id                 uuid,
    raised_by_profile_id    uuid,
    note                    varchar(255),
    raised_at               timestamp(6) with time zone,
    primary key (id)
);
-- SosAlertRepository.findByTripIdOrderByRaisedAtDesc / findAllByOrderByRaisedAtDesc
-- (CLAUDE.md C.6) — the CSKH worklist is sorted by recency, both globally and per-trip.
create index idx_sos_alerts_trip_raised_at on sos_alerts (trip_id, raised_at desc);
create index idx_sos_alerts_raised_at on sos_alerts (raised_at desc);

create table payments (
    id              uuid not null,
    trip_id         uuid,
    customer_id     uuid,
    amount          numeric(38, 2),
    currency        varchar(255),
    method          varchar(255) check (method in ('CASH', 'IN_APP')),
    status          varchar(255) check (status in ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    retry_count     integer not null,
    primary key (id)
);
-- PaymentRepository.findByTripId — exactly one payment opens per completed trip
-- (CLAUDE.md flow step 10); unique enforces that invariant at the data layer too.
create unique index idx_payments_trip_id on payments (trip_id);

create table ratings (
    id                  uuid not null,
    trip_id             uuid,
    rater_user_id       uuid,
    ratee_user_id       uuid,
    score               integer not null,
    comment             varchar(255),
    complaint           boolean not null,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    primary key (id)
);
-- RatingRepository: findByTripId / existsByTripIdAndRaterUserId (one rating per
-- rater per trip — CLAUDE.md flow step 11), findByRateeUserId (a driver's/customer's
-- received ratings), findByComplaintTrueOrderByCreatedAtDesc (CLAUDE.md F.1 admin queue).
create index idx_ratings_trip_rater on ratings (trip_id, rater_user_id);
create index idx_ratings_ratee on ratings (ratee_user_id);
create index idx_ratings_complaint_created_at on ratings (complaint, created_at desc);

create table notifications (
    id              uuid not null,
    recipient_id    uuid,
    type            varchar(255) check (type in ('BOOKING_CONFIRMED', 'TRIP_COMPLETED', 'SOS_RAISED')),
    message         varchar(255),
    read            boolean not null,
    created_at      timestamp(6) with time zone,
    primary key (id)
);
-- NotificationRepository.findByRecipientIdOrderByCreatedAtDesc — every "my notifications" read.
create index idx_notifications_recipient_created_at on notifications (recipient_id, created_at desc);

create table location_updates (
    id              uuid not null,
    driver_id       uuid,
    latitude        float8 not null,
    longitude       float8 not null,
    recorded_at     timestamp(6) with time zone,
    primary key (id)
);
-- LocationUpdateRepository.findByDriverIdOrderByRecordedAtDesc — GPS trail playback,
-- the dispute-resolution evidence trail CLAUDE.md D.2 / C.5 calls for.
create index idx_location_updates_driver_recorded_at on location_updates (driver_id, recorded_at desc);
