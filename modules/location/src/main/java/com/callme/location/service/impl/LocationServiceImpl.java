package com.callme.location.service.impl;

import com.callme.common.event.DriverLocationUpdatedEvent;
import com.callme.common.shared.GeoPoint;
import com.callme.location.entity.LocationUpdate;
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
    private final ApplicationEventPublisher events;

    public LocationServiceImpl(LocationUpdateRepository locationUpdateRepository, ApplicationEventPublisher events) {
        this.locationUpdateRepository = locationUpdateRepository;
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
}
