package com.callme.driver.event;

import com.callme.common.event.DriverUnresponsiveEvent;
import com.callme.common.event.TripCancelledEvent;
import com.callme.common.event.TripCompletedEvent;
import com.callme.driver.service.DriverService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Releases a driver back into the matching pool once their trip ends. Claiming the
 * driver happens synchronously and atomically during matching itself
 * ({@code DriverReservationPort}, CLAUDE.md line 59 / B.1) — by the time a booking is
 * ever persisted as CONFIRMED the driver is already reserved, so only the *release*
 * half of the lifecycle needs to react to events here.
 * Reacts only to shared event types declared in `common` — never to trip's internals.
 */
@Component
public class TripLifecycleEventListener {

    private final DriverService driverService;

    public TripLifecycleEventListener(DriverService driverService) {
        this.driverService = driverService;
    }

    @EventListener
    public void onTripCompleted(TripCompletedEvent event) {
        driverService.markAvailable(event.driverId());
    }

    @EventListener
    public void onTripCancelled(TripCancelledEvent event) {
        driverService.markAvailable(event.driverId());
    }

    /** CLAUDE.md B.3 — records a strike so this driver sinks in future matching priority after going unresponsive. */
    @EventListener
    public void onDriverUnresponsive(DriverUnresponsiveEvent event) {
        driverService.recordNoResponseStrike(event.driverId());
    }
}
