package com.callme.trip.repository;

import com.callme.trip.entity.Trip;
import com.callme.trip.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * CLAUDE.md G — Postgres transaction-scoped advisory lock guarding the periodic
     * sweeps against double-firing when more than one app instance runs. Non-blocking:
     * {@code false} means another instance holds this sweep's lock right now, so this
     * cycle simply skips (the work is idempotent-per-cycle, the next cycle retries).
     * Transaction-scoped ({@code _xact_}) so the lock can never leak — it releases
     * with the sweep's own commit/rollback, with no unlock bookkeeping to forget.
     */
    @Query(value = "select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryXactLock(@Param("key") long key);
}
