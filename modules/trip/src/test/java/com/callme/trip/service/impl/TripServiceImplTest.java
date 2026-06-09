package com.callme.trip.service.impl;

import com.callme.common.event.TripAbortedMidwayEvent;
import com.callme.common.exception.ForbiddenException;
import com.callme.common.port.DriverLocationFreshnessPort;
import com.callme.common.port.DriverRouteTracePort;
import com.callme.common.port.FareEstimationPort;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.CancellationReason;
import com.callme.common.shared.GeoPoint;
import com.callme.trip.entity.EmergencyAbortReport;
import com.callme.trip.entity.Trip;
import com.callme.trip.repository.EmergencyAbortReportRepository;
import com.callme.trip.repository.RouteDeviationFlagRepository;
import com.callme.trip.repository.SosAlertRepository;
import com.callme.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md E.2 — "tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS... đây là
 * tình huống nghiêm trọng nhất trong toàn bộ domain". Verifies the driver can no
 * longer use the ordinary {@code cancel} as a casual escape hatch once they're
 * physically holding the customer's car, and that the dedicated abort flow both
 * requires a declared safe location and raises the CSKH worklist entry.
 */
class TripServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-06-07T22:00:00Z");

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final SosAlertRepository sosAlertRepository = mock(SosAlertRepository.class);
    private final RouteDeviationFlagRepository routeDeviationFlagRepository = mock(RouteDeviationFlagRepository.class);
    private final EmergencyAbortReportRepository emergencyAbortReportRepository = mock(EmergencyAbortReportRepository.class);
    private final FareEstimationPort fareEstimationPort = mock(FareEstimationPort.class);
    private final DriverLocationFreshnessPort driverLocationFreshnessPort = mock(DriverLocationFreshnessPort.class);
    private final DriverRouteTracePort driverRouteTracePort = mock(DriverRouteTracePort.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);

    private final TripServiceImpl service = new TripServiceImpl(tripRepository, sosAlertRepository, routeDeviationFlagRepository,
            emergencyAbortReportRepository, fareEstimationPort, driverLocationFreshnessPort, driverRouteTracePort, events);

    private final AuthenticatedAccount driver = new AuthenticatedAccount(UUID.randomUUID(), DRIVER_ID, AccountRole.DRIVER);
    private final AuthenticatedAccount customer = new AuthenticatedAccount(UUID.randomUUID(), CUSTOMER_ID, AccountRole.CUSTOMER);

    private Trip inProgressTrip() {
        var trip = new Trip(UUID.randomUUID(), CUSTOMER_ID, DRIVER_ID, 10.0, 106.0, 10.5, 106.5);
        trip.arriveAtPickup(T0);
        trip.pickUpCustomer(true, T0.plusSeconds(60));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return trip;
    }

    @Test
    void driverCannotUseTheOrdinaryCancelOnceTheyHaveTheWheel() {
        inProgressTrip();

        assertThatThrownBy(() -> service.cancel(TRIP_ID, driver))
                .isInstanceOf(ForbiddenException.class);

        verify(emergencyAbortReportRepository, never()).save(any());
    }

    @Test
    void customerCanStillCancelAnInProgressTripThroughTheOrdinaryPath() {
        var trip = inProgressTrip();

        service.cancel(TRIP_ID, customer);

        assertThat(trip.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
    }

    @Test
    void abortRequiresTheTripToActuallyBeInProgress() {
        var trip = new Trip(UUID.randomUUID(), CUSTOMER_ID, DRIVER_ID, 10.0, 106.0, 10.5, 106.5);
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.abortInProgressTrip(TRIP_ID, driver, new GeoPoint(10.1, 106.1), "kẹt xe"))
                .isInstanceOf(ForbiddenException.class);

        verify(emergencyAbortReportRepository, never()).save(any());
    }

    @Test
    void onlyTheAssignedDriverCanAbortMidway() {
        inProgressTrip();

        assertThatThrownBy(() -> service.abortInProgressTrip(TRIP_ID, customer, new GeoPoint(10.1, 106.1), "kẹt xe"))
                .isInstanceOf(ForbiddenException.class);

        verify(emergencyAbortReportRepository, never()).save(any());
    }

    @Test
    void abortingMidwayCancelsWithTheDedicatedReasonAndRaisesTheCskhWorklistEntry() {
        var trip = inProgressTrip();
        var safeLocation = new GeoPoint(10.2, 106.2);
        when(emergencyAbortReportRepository.save(any(EmergencyAbortReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.abortInProgressTrip(TRIP_ID, driver, safeLocation, "xe có dấu hiệu cháy, đã đưa khách xuống an toàn");

        assertThat(trip.getCancellationReason()).isEqualTo(CancellationReason.DRIVER_EMERGENCY_ABORT);
        verify(emergencyAbortReportRepository).save(any(EmergencyAbortReport.class));
        verify(events).publishEvent(any(TripAbortedMidwayEvent.class));
    }
}
