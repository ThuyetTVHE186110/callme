package com.callme.driver.event;

import com.callme.common.event.DriverLocationUpdatedEvent;
import com.callme.driver.service.DriverService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refreshes the "last known location" used by {@link com.callme.common.port.DriverAvailabilityPort}
 * matching whenever the location module reports a fresh GPS fix — keeps driver decoupled
 * from how/where raw location history is stored.
 */
@Component
public class DriverLocationEventListener {

    private final DriverService driverService;

    public DriverLocationEventListener(DriverService driverService) {
        this.driverService = driverService;
    }

    @EventListener
    public void onDriverLocationUpdated(DriverLocationUpdatedEvent event) {
        driverService.updateLocation(event.driverId(), event.location().latitude(), event.location().longitude());
    }
}
