package com.callme.booking.repository;

import com.callme.booking.entity.Booking;
import com.callme.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Backs idempotent booking creation (CLAUDE.md A.3) — same customer + same client-supplied key replays the original booking. */
    Optional<Booking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    /** Backs the concurrent-booking guard (CLAUDE.md A.6) — a customer can only be in one car at a time. */
    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<BookingStatus> statuses);
}
