package com.callme.driver.service.impl;

import com.callme.common.port.DriverLocationFreshnessPort;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Implements the cross-module contract published in `common` — backs trip's GPS-loss sweep (CLAUDE.md C.7). */
@Service
public class DriverLocationFreshnessPortImpl implements DriverLocationFreshnessPort {

    private final DriverRepository driverRepository;

    public DriverLocationFreshnessPortImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public Optional<Instant> lastReportedAt(UUID driverId) {
        return driverRepository.findById(driverId).map(Driver::getLastLocationUpdatedAt);
    }
}
