package com.callme.driver.service.impl;

import com.callme.common.event.DriverForcedOfflineEvent;
import com.callme.common.exception.NotFoundException;
import com.callme.driver.dto.DriverResponse;
import com.callme.driver.dto.OnlineDriverResponse;
import com.callme.driver.entity.BackgroundCheckStatus;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import com.callme.driver.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DriverServiceImpl implements DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceImpl.class);

    /** CLAUDE.md G — advisory-lock key for {@link #sweepExpiredVerifications} (multi-instance double-fire guard); unique across the app. */
    private static final long VERIFICATION_SWEEP_LOCK_KEY = 1004L;

    private final DriverRepository driverRepository;
    private final ApplicationEventPublisher events;

    public DriverServiceImpl(DriverRepository driverRepository, ApplicationEventPublisher events) {
        this.driverRepository = driverRepository;
        this.events = events;
    }

    /** Public projection only — id + display name. The verification dossier is admin data ({@link #getDriver}), not fleet-wide public reading (OWASP A01). */
    @Override
    public List<OnlineDriverResponse> listOnlineDrivers() {
        return driverRepository.findByOnlineTrue().stream()
                .map(driver -> new OnlineDriverResponse(driver.getId(), driver.getDisplayName()))
                .toList();
    }

    @Override
    public DriverResponse getDriver(UUID driverId) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        return toResponse(driver, Instant.now());
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
            driver.goOnline(Instant.now());
        } else {
            driver.goOffline();
        }
        driverRepository.save(driver);
    }

    @Override
    public void recordVerification(UUID driverId, BackgroundCheckStatus backgroundCheckStatus, LocalDate licenseExpiryDate, LocalDate insuranceValidUntil) {
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
        driver.recordVerification(backgroundCheckStatus, licenseExpiryDate, insuranceValidUntil, Instant.now());
        driverRepository.save(driver);
    }

    private DriverResponse toResponse(Driver driver, Instant now) {
        return new DriverResponse(driver.getId(), driver.getDisplayName(), driver.isOnline(),
                driver.getBackgroundCheckStatus(), driver.getLicenseExpiryDate(), driver.getInsuranceValidUntil(),
                driver.getLastReverificationAt(), driver.isEligibleToGoOnline(now));
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

    /**
     * CLAUDE.md §4.5 invariant — "Driver không thể chuyển online = true nếu bất kỳ
     * xác minh nào đã hết hạn" must hold CONTINUOUSLY, not just at the goOnline
     * transition: a license/insurance/reverification lapsing mid-shift previously
     * left the driver online and matchable indefinitely. Hourly is plenty — the
     * underlying expiries have day granularity (LocalDate / a 180-day interval).
     *
     * Force-offline only stops NEW matches: a driver currently mid-trip (onTrip)
     * keeps driving to a safe completion — yanking an active trip would create the
     * exact mid-route emergency E.2 exists to prevent. They simply can't go back
     * online afterwards until re-verified (goOnline already refuses them).
     */
    @Scheduled(fixedDelayString = "PT1H")
    void sweepExpiredVerifications() {
        if (!driverRepository.tryAdvisoryXactLock(VERIFICATION_SWEEP_LOCK_KEY)) {
            return; // another instance is running this sweep right now (CLAUDE.md G)
        }
        var now = Instant.now();
        for (var driver : driverRepository.findByOnlineTrue()) {
            var reasons = driver.ineligibilityReasons(now);
            if (reasons.isEmpty()) {
                continue;
            }
            // One sour row (e.g. an optimistic-lock race with the driver toggling
            // their own status) must not abort the whole batch — every other lapsed
            // driver still needs to leave the matching pool this cycle.
            try {
                driver.goOffline();
                driverRepository.save(driver);
                String joined = String.join("; ", reasons);
                log.warn("Driver {} forced offline — verification lapsed while online: {}", driver.getId(), joined);
                events.publishEvent(new DriverForcedOfflineEvent(driver.getId(), joined));
            } catch (RuntimeException e) {
                log.error("Verification-expiry sweep failed for driver {} — skipping them this cycle, the next sweep retries", driver.getId(), e);
            }
        }
    }
}
