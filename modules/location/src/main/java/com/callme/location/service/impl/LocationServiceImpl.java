package com.callme.location.service.impl;

import com.callme.common.event.CustomerLocationUpdatedEvent;
import com.callme.common.event.DriverLocationUpdatedEvent;
import com.callme.common.shared.GeoPoint;
import com.callme.location.entity.CustomerLocationUpdate;
import com.callme.location.entity.LocationUpdate;
import com.callme.location.repository.CustomerLocationUpdateRepository;
import com.callme.location.repository.LocationUpdateRepository;
import com.callme.location.service.LocationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private final LocationUpdateRepository locationUpdateRepository;
    private final CustomerLocationUpdateRepository customerLocationUpdateRepository;
    private final ApplicationEventPublisher events;

    public LocationServiceImpl(LocationUpdateRepository locationUpdateRepository,
                               CustomerLocationUpdateRepository customerLocationUpdateRepository,
                               ApplicationEventPublisher events) {
        this.locationUpdateRepository = locationUpdateRepository;
        this.customerLocationUpdateRepository = customerLocationUpdateRepository;
        this.events = events;
    }

    /**
     * Persists the raw GPS fix as history (audit trail for fare disputes / route
     * verification — see CLAUDE.md edge cases C.7/C.8), then republishes only the
     * latest position so the driver module can refresh its matching cache without
     * depending on this module's storage model.
     */
    @Override
    public void reportLocation(UUID driverId, double latitude, double longitude) {
        locationUpdateRepository.save(new LocationUpdate(driverId, latitude, longitude));
        events.publishEvent(new DriverLocationUpdatedEvent(driverId, new GeoPoint(latitude, longitude)));
    }

    /** Same shape as {@link #reportLocation} but for the customer side — see CLAUDE.local.md §3 (driver sees customer's location). */
    @Override
    public void reportCustomerLocation(UUID customerId, double latitude, double longitude) {
        customerLocationUpdateRepository.save(new CustomerLocationUpdate(customerId, latitude, longitude));
        events.publishEvent(new CustomerLocationUpdatedEvent(customerId, new GeoPoint(latitude, longitude)));
    }
}
