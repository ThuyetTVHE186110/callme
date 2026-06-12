package com.callme.trip.event;

import com.callme.common.event.BookingCancelledEvent;
import com.callme.common.event.BookingConfirmedEvent;
import com.callme.trip.service.TripService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to booking lifecycle changes in another module. Trip only depends on the
 * shared event types declared in `common` — never on booking's internals — so this
 * module's pom does not need a dependency on the booking module.
 */
@Component
public class BookingEventListener {

    private final TripService tripService;

    public BookingEventListener(TripService tripService) {
        this.tripService = tripService;
    }

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        tripService.startTrip(event.bookingId(), event.customerId(), event.driverId(), event.pickup(), event.destination());
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        tripService.cancelForBooking(event.bookingId(), event.reason());
    }
}
