package com.callme.dispatch.service.impl;

import com.callme.common.port.DriverAvailabilityPort;
import com.callme.common.port.DriverReservationPort;
import com.callme.common.port.dto.DriverSummary;
import com.callme.common.shared.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md B.3/B.4 — the matching order ("hạ điểm ưu tiên hiển thị") and the
 * re-dispatch exclusion. Strikes cost ground as a distance handicap, not an
 * absolute rank — one bad night must not be a lifetime ban behind every clean
 * driver however far away.
 */
class DriverMatchingPortImplTest {

    private static final GeoPoint PICKUP = new GeoPoint(10.776, 106.701);

    private final DriverAvailabilityPort availabilityPort = mock(DriverAvailabilityPort.class);
    private final DriverReservationPort reservationPort = mock(DriverReservationPort.class);
    private final DriverMatchingPortImpl matching = new DriverMatchingPortImpl(availabilityPort, reservationPort);

    /** A candidate roughly {@code km} kilometres north of the pickup point. */
    private DriverSummary candidateAtKm(UUID id, double km, int strikes) {
        return new DriverSummary(id, "driver-" + km, new GeoPoint(PICKUP.latitude() + km / 111.0, PICKUP.longitude()), strikes);
    }

    @Test
    void aLightlyStruckNearbyDriverStillBeatsACleanFarawayOne() {
        var nearWithStrike = UUID.randomUUID();   // 0.2 km + 1 strike × 2 km = 2.2 km effective
        var cleanButFar = UUID.randomUUID();      // 10 km + 0 strikes        = 10 km effective
        when(availabilityPort.findAvailableNear(any(), anyDouble())).thenReturn(List.of(
                candidateAtKm(cleanButFar, 10.0, 0),
                candidateAtKm(nearWithStrike, 0.2, 1)));
        when(reservationPort.tryReserve(any())).thenReturn(true);

        assertThat(matching.matchDriver(PICKUP, 15.0, null)).contains(nearWithStrike);
    }

    @Test
    void chronicStrikesStillSinkAnOtherwiseNearestDriver() {
        var nearButChronic = UUID.randomUUID();   // 0.2 km + 5 strikes × 2 km = 10.2 km effective
        var cleanAndClose = UUID.randomUUID();    // 3 km + 0 strikes          = 3 km effective
        when(availabilityPort.findAvailableNear(any(), anyDouble())).thenReturn(List.of(
                candidateAtKm(nearButChronic, 0.2, 5),
                candidateAtKm(cleanAndClose, 3.0, 0)));
        when(reservationPort.tryReserve(any())).thenReturn(true);

        assertThat(matching.matchDriver(PICKUP, 15.0, null)).contains(cleanAndClose);
    }

    /** CLAUDE.md B.4 — the driver who just withdrew from this booking is skipped even when they are the only/best candidate. */
    @Test
    void theWithdrawnDriverIsNeverHandedTheSameJobBack() {
        var withdrawn = UUID.randomUUID();
        var replacement = UUID.randomUUID();
        when(availabilityPort.findAvailableNear(any(), anyDouble())).thenReturn(List.of(
                candidateAtKm(withdrawn, 0.1, 0),
                candidateAtKm(replacement, 4.0, 0)));
        when(reservationPort.tryReserve(any())).thenReturn(true);

        assertThat(matching.matchDriver(PICKUP, 15.0, withdrawn)).contains(replacement);
    }

    @Test
    void reportsNoDriverWhenTheOnlyCandidateIsTheWithdrawnOne() {
        var withdrawn = UUID.randomUUID();
        when(availabilityPort.findAvailableNear(any(), anyDouble())).thenReturn(List.of(
                candidateAtKm(withdrawn, 0.1, 0)));

        assertThat(matching.matchDriver(PICKUP, 15.0, withdrawn)).isEmpty();
    }
}
