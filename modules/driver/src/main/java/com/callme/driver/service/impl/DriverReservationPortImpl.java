package com.callme.driver.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.port.DriverReservationPort;
import com.callme.driver.repository.DriverRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implements the cross-module contract published in `common`. Claiming happens via
 * {@code saveAndFlush} so a concurrent reservation surfaces as an optimistic-lock
 * conflict right here — synchronously, before the caller ever commits a booking —
 * rather than as a half-applied state discovered later (CLAUDE.md line 59 / B.1).
 */
@Service
public class DriverReservationPortImpl implements DriverReservationPort {

    private final DriverRepository driverRepository;

    public DriverReservationPortImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public boolean tryReserve(UUID driverId) {
        var driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null || !driver.isOnline() || driver.isOnTrip()) {
            return false;
        }
        try {
            driver.beginTrip();
            driverRepository.saveAndFlush(driver);
            return true;
        } catch (OptimisticLockingFailureException | ConflictException raceLost) {
            return false;
        }
    }

    @Override
    public void release(UUID driverId) {
        driverRepository.findById(driverId).ifPresent(driver -> {
            driver.endTrip();
            driverRepository.save(driver);
        });
    }
}
