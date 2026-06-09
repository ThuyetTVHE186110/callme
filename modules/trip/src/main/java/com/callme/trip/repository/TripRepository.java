package com.callme.trip.repository;

import com.callme.trip.entity.Trip;
import com.callme.trip.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    /**
     * CLAUDE.md B.4 — re-dispatching after a driver withdraws creates a NEW Trip row
     * for the same booking (the old one is cancelled, terminal). A booking can
     * therefore own more than one Trip over its lifetime, but at most one
     * non-terminal one at a time — `findFirstBy...` keeps this safe (returns the
     * live one) even if a stale terminal row from an earlier assignment still exists,
     * where a plain `findByBookingId` would blow up with "non-unique result".
     */
    Optional<Trip> findFirstByBookingIdAndStatusInOrderByIdDesc(UUID bookingId, Collection<TripStatus> statuses);

    /** CLAUDE.md B.3 — feeds the unresponsive-driver sweep: STARTED trips that have sat past the response-timeout threshold without the driver reaching pickup. */
    List<Trip> findAllByStatusAndCreatedAtBefore(TripStatus status, Instant threshold);

    /** CLAUDE.md C.7 — feeds the GPS-loss sweep: every trip currently underway (driver holding the customer's car). */
    List<Trip> findAllByStatus(TripStatus status);

    /** Returns the most recent trip for a booking regardless of status — used by the by-booking lookup endpoint. */
    Optional<Trip> findFirstByBookingIdOrderByIdDesc(UUID bookingId);
}
