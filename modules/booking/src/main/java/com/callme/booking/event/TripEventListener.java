package com.callme.booking.event;

import com.callme.common.event.DriverCancelledBeforePickupEvent;
import com.callme.common.event.TripCancelledEvent;
import com.callme.common.event.TripCompletedEvent;
import com.callme.booking.service.BookingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to trip lifecycle changes in another module. Booking only depends on the
 * shared event types declared in `common` — never on trip's internals — so this
 * module's pom does not need a dependency on the trip module (mirrors how trip's
 * own {@code BookingEventListener} reacts to booking events without depending on it).
 *
 * <p>All three handlers are plain {@code @EventListener}s — they update business
 * state that must stay atomic with the originating trip transition (CLAUDE.md G.1).
 */
@Component
public class TripEventListener {

    private final BookingService bookingService;

    public TripEventListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * CLAUDE.md B.4 — the assigned driver backed out before reaching the customer.
     * The customer's request must survive: re-run matching for a replacement instead
     * of leaving them with a CONFIRMED booking pointing at a driver who vanished.
     * The withdrawing driver is excluded from this re-match (CLAUDE.md B.3) — they
     * were just released back into the pool and would otherwise often be the nearest
     * candidate, i.e. matched straight back to the customer they abandoned.
     */
    @EventListener
    public void onDriverCancelledBeforePickup(DriverCancelledBeforePickupEvent event) {
        bookingService.rematchAfterDriverWithdrawal(event.bookingId(), event.previousDriverId());
    }

    /** Flow step 9→12 — the ride happened; settle the booking so the A.6 one-active-booking rule stops counting it. */
    @EventListener
    public void onTripCompleted(TripCompletedEvent event) {
        bookingService.completeForTrip(event.bookingId());
    }

    /**
     * The trip ended without completing — close the booking in lockstep, classified
     * by the trip's own reason (no-show, vehicle breakdown, emergency abort, customer
     * cancel...). Reasons that must NOT end the booking (re-dispatch and cascade-origin
     * cases) are filtered inside {@link BookingService#closeAfterTripCancellation}.
     */
    @EventListener
    public void onTripCancelled(TripCancelledEvent event) {
        bookingService.closeAfterTripCancellation(event.bookingId(), event.reason());
    }
}
