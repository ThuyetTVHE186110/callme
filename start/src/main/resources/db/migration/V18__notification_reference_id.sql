-- CLAUDE.md §4.8 / notification deep-linking: adds a nullable reference column to
-- notifications so the mobile app can navigate directly to the relevant trip or booking
-- screen without a separate lookup. Null for non-trip notifications (e.g. VERIFICATION_EXPIRED).
--
-- For BOOKING_CONFIRMED events the referenceId is bookingId (closest available at
-- publish time — tripId is not in the event payload); for all other trip-related events
-- (SOS_RAISED, GPS_SIGNAL_LOST, TRIP_COMPLETED, TRIP_ABORTED_MIDWAY,
-- DESTINATION_CHANGED, INCIDENT_REPORTED) it is tripId.
alter table notifications add column reference_id uuid;
