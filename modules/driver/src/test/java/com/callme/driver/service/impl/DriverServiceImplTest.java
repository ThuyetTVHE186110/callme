package com.callme.driver.service.impl;

import com.callme.common.event.DriverForcedOfflineEvent;
import com.callme.driver.entity.BackgroundCheckStatus;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md §4.5 — the "no online driver with lapsed verification" invariant must
 * hold continuously, not only at the goOnline transition: a license or insurance
 * expiring mid-shift has to take the driver out of the matching pool, with an
 * explanation, instead of leaving them dispatchable indefinitely.
 */
class DriverServiceImplTest {

    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DriverServiceImpl service = new DriverServiceImpl(driverRepository, events);

    private Driver onlineDriver(LocalDate licenseExpiry) {
        var driver = new Driver("Trần Văn B");
        var verifiedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        driver.recordVerification(BackgroundCheckStatus.APPROVED, licenseExpiry, LocalDate.now().plusYears(1), verifiedAt);
        if (driver.isEligibleToGoOnline(Instant.now())) {
            driver.goOnline(Instant.now());
        }
        return driver;
    }

    @Test
    void aDriverWhoseLicenseLapsedMidShiftIsForcedOfflineAndTold() {
        // Online since yesterday; license expired this morning — goOnline's gate never saw it.
        var driver = onlineDriver(LocalDate.now().plusDays(1));
        var lapsed = onlineDriver(LocalDate.now().plusDays(1));
        forceLicenseIntoThePast(lapsed);
        when(driverRepository.tryAdvisoryXactLock(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(driverRepository.findByOnlineTrue()).thenReturn(List.of(driver, lapsed));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sweepExpiredVerifications();

        assertThat(lapsed.isOnline()).isFalse();
        assertThat(driver.isOnline()).isTrue();
        verify(events).publishEvent(any(DriverForcedOfflineEvent.class));
    }

    @Test
    void fullyVerifiedOnlineDriversAreLeftAlone() {
        var driver = onlineDriver(LocalDate.now().plusYears(1));
        when(driverRepository.tryAdvisoryXactLock(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(driverRepository.findByOnlineTrue()).thenReturn(List.of(driver));

        service.sweepExpiredVerifications();

        assertThat(driver.isOnline()).isTrue();
        verify(events, never()).publishEvent(any(DriverForcedOfflineEvent.class));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    /** Re-record the verification with an already-expired license while keeping the driver online — exactly what the passage of time does. */
    private void forceLicenseIntoThePast(Driver driver) {
        driver.recordVerification(BackgroundCheckStatus.APPROVED, LocalDate.now().minusDays(1), LocalDate.now().plusYears(1),
                Instant.now().minus(1, ChronoUnit.DAYS));
    }
}
