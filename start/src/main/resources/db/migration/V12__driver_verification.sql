-- CLAUDE.md §4.5/§4.2 — "Driver không thể chuyển online = true nếu bất kỳ xác minh nào đã
-- hết hạn". A new driver starts PENDING/unverified and is not eligible to go online until
-- an admin records a reverification round (Driver.recordVerification).

alter table drivers
    add column background_check_status varchar(20) not null default 'PENDING';
alter table drivers
    add constraint drivers_background_check_status_check
        check (background_check_status in ('PENDING', 'APPROVED', 'REJECTED'));

alter table drivers
    add column license_expiry_date date;

alter table drivers
    add column insurance_valid_until date;

alter table drivers
    add column last_reverification_at timestamp(6) with time zone;
