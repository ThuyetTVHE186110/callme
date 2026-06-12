package com.callme.payment.event;

import com.callme.common.event.TripCompletedEvent;
import com.callme.payment.service.PaymentService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens the payment record the instant a trip finishes (CLAUDE.md flow step 10) —
 * payment depends only on the shared event type from `common`, never on trip's internals.
 */
@Component
public class TripCompletedEventListener {

    private final PaymentService paymentService;

    public TripCompletedEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @EventListener
    public void onTripCompleted(TripCompletedEvent event) {
        paymentService.openForTrip(event.tripId(), event.customerId(), event.driverId(),
                event.finalFare().amount(), event.finalFare().currency().getCurrencyCode());
    }
}
