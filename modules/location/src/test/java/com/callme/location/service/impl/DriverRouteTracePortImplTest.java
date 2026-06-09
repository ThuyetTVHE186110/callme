package com.callme.location.service.impl;

import com.callme.common.shared.GeoPoint;
import com.callme.location.entity.LocationUpdate;
import com.callme.location.repository.LocationUpdateRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CLAUDE.md C.8 — verifies the GPS-trail replay that {@code TripServiceImpl.checkRouteDeviation} compares against the charged straight-line distance. */
class DriverRouteTracePortImplTest {

    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-06-07T22:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-07T22:30:00Z");

    private final LocationUpdateRepository repository = mock(LocationUpdateRepository.class);
    private final DriverRouteTracePortImpl port = new DriverRouteTracePortImpl(repository);

    private LocationUpdate pingAt(double latitude, double longitude) {
        return new LocationUpdate(DRIVER_ID, latitude, longitude);
    }

    @Nested
    class CumulativeDistance {

        @Test
        void sumsConsecutivePingDistances() {
            // Three pings walking roughly straight north — ~0.111 km of latitude per 0.001°.
            when(repository.findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(DRIVER_ID), any(), any()))
                    .thenReturn(List.of(
                            pingAt(10.7700, 106.7000),
                            pingAt(10.7710, 106.7000),
                            pingAt(10.7720, 106.7000)));

            double total = port.cumulativeDistanceKm(DRIVER_ID, FROM, TO);

            assertThat(total).isCloseTo(0.222, within(0.01));
        }

        @Test
        void returnsZeroForFewerThanTwoPings() {
            when(repository.findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(DRIVER_ID), any(), any()))
                    .thenReturn(List.of(pingAt(10.7700, 106.7000)));

            assertThat(port.cumulativeDistanceKm(DRIVER_ID, FROM, TO)).isZero();
        }

        @Test
        void returnsZeroWhenNoPingsRecorded() {
            when(repository.findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(DRIVER_ID), any(), any()))
                    .thenReturn(List.of());

            assertThat(port.cumulativeDistanceKm(DRIVER_ID, FROM, TO)).isZero();
        }

        @Test
        void detectsADetourAsLongerThanADirectPath() {
            // Direct: (10.770,106.700) -> (10.780,106.700), straight-line ~1.11 km.
            // Detour: same endpoints via a midpoint well off the direct line.
            when(repository.findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(DRIVER_ID), any(), any()))
                    .thenReturn(List.of(
                            pingAt(10.7700, 106.7000),
                            pingAt(10.7750, 106.7100),
                            pingAt(10.7800, 106.7000)));

            double detourTotal = port.cumulativeDistanceKm(DRIVER_ID, FROM, TO);
            double directDistance = new GeoPoint(10.7700, 106.7000)
                    .distanceKm(new GeoPoint(10.7800, 106.7000));

            assertThat(detourTotal).isGreaterThan(directDistance);
        }
    }
}
