package com.callme.driver.service.impl;

import com.callme.common.exception.NotFoundException;
import com.callme.driver.dto.DriverResponse;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import com.callme.driver.service.DriverService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    public DriverServiceImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public List<DriverResponse> listOnlineDrivers() {
        return driverRepository.findByOnlineTrue().stream()
                .map(driver -> new DriverResponse(driver.getId(), driver.getDisplayName(), driver.isOnline()))
                .toList();
    }

    @Override
    public UUID register(String displayName) {
        return driverRepository.save(new Driver(displayName)).getId();
    }

    @Override
    public void setOnline(UUID driverId, boolean online) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        if (online) {
            driver.goOnline();
        } else {
            driver.goOffline();
        }
        driverRepository.save(driver);
    }

    @Override
    public void updateLocation(UUID driverId, double latitude, double longitude) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        driver.updateLocation(latitude, longitude, Instant.now());
        driverRepository.save(driver);
    }

    @Override
    public void markAvailable(UUID driverId) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        driver.endTrip();
        driverRepository.save(driver);
    }

    @Override
    public void recordNoResponseStrike(UUID driverId) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        driver.recordNoResponseStrike();
        driverRepository.save(driver);
    }
}
