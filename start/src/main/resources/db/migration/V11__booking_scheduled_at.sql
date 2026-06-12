-- CLAUDE.md §4.7 — "đặt lịch trước"; null means "ngay bây giờ" (existing immediate-matching
-- behaviour, unchanged). Non-null bookings stay PENDING, untouched by matching, until the
-- scheduled-booking sweep (BookingServiceImpl.sweepScheduledBookings) brings them within
-- Booking.SCHEDULED_MATCH_LEAD_TIME of scheduledAt.

alter table bookings
    add column scheduled_at timestamp(6) with time zone;

-- Backs findByStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqual — the sweep query.
create index idx_bookings_status_scheduled_at on bookings (status, scheduled_at);
