package com.callme.booking.repository;

import com.callme.booking.entity.Booking;
import com.callme.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Backs idempotent booking creation (CLAUDE.md A.3) — same customer + same client-supplied key replays the original booking. */
    Optional<Booking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    /**
     * Backs the concurrent-booking guard (CLAUDE.md A.6) — a customer can only be in
     * one car at a time, but a not-yet-due advance booking (§4.7) is not "in a car",
     * so the guard loads the handful of active rows and classifies them in memory
     * (immediate-or-due vs future-scheduled) rather than asking a yes/no exists query.
     */
    List<Booking> findByCustomerIdAndStatusIn(UUID customerId, Collection<BookingStatus> statuses);

    /**
     * CLAUDE.md §4.7 — backs the scheduled-booking sweep: advance bookings still
     * {@code PENDING} (matching deliberately deferred at creation time) whose
     * {@code scheduledAt} has now entered the {@code SCHEDULED_MATCH_LEAD_TIME} window.
     * Immediate bookings (scheduledAt = null) never match this — Postgres comparisons
     * against NULL are never true.
     */
    List<Booking> findByStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqual(BookingStatus status, Instant threshold);

    /** CLAUDE.md G — multi-instance sweep guard; see {@code TripRepository#tryAdvisoryXactLock} for the full rationale. */
    @Query(value = "select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryXactLock(@Param("key") long key);
}
