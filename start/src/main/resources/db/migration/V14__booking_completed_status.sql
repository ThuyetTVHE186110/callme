-- Booking lifecycle closure: a finished ride must settle its booking as COMPLETED.
-- Without this terminal state the booking sat in CONFIRMED forever after its trip
-- completed, permanently tripping the one-active-booking rule (CLAUDE.md A.6) and
-- locking the customer out of ever booking again. Trip-level cancellations now also
-- close the booking in lockstep (reason-aware cascade — see the §2 invariant note).
alter table bookings
    drop constraint bookings_status_check;
alter table bookings
    add constraint bookings_status_check
        check (status in ('PENDING', 'CONFIRMED', 'NO_DRIVER_FOUND', 'CANCELLED', 'COMPLETED'));

-- Backfill: bookings whose most recent trip already finished were stuck CONFIRMED
-- under the old behaviour — settle them so their customers are unblocked.
update bookings b
set status = 'COMPLETED'
where b.status = 'CONFIRMED'
  and exists (select 1 from trips t where t.booking_id = b.id and t.status = 'COMPLETED');
