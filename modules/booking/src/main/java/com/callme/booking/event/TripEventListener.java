package com.callme.booking.event;

import com.callme.common.event.DriverCancelledBeforePickupEvent;
import com.callme.booking.service.BookingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to trip lifecycle changes in another module. Booking only depends on the
 * shared event types declared in `common` — never on trip's internals — so this
 * module's pom does not need a dependency on the trip module (mirrors how trip's
 * own {@code BookingEventListener} reacts to booking events without depending on it).
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
     */
    @EventListener
    public void onDriverCancelledBeforePickup(DriverCancelledBeforePickupEvent event) {
        bookingService.rematchAfterDriverWithdrawal(event.bookingId());
    }
}
