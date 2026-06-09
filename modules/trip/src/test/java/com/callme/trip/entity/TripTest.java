package com.callme.trip.entity;

import com.callme.common.shared.CancellationReason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md §2 invariants + §5 lifecycle split: a {@link Trip} must walk
 * STARTED → ARRIVED_AT_PICKUP → IN_PROGRESS → COMPLETED in order, never skip or
 * reverse, and every transition must be gated to the one role that can attest to it.
 * These are pure state-machine rules — no Spring context needed to pin them down.
 */
class TripTest {

    private static final Instant NOW = Instant.parse("2026-06-07T15:00:00Z");

    private Trip newTrip() {
        return new Trip(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                10.0, 106.0, 10.05, 106.05);
    }

    @Test
    void startsInStartedStatus() {
        var trip = newTrip();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTED);
        assertThat(trip.isTerminal()).isFalse();
    }

    @Nested
    class HappyPath {

        @Test
        void walksThroughTheFullLifecycleInOrder() {
            var trip = newTrip();

            trip.arriveAtPickup(NOW);
            assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED_AT_PICKUP);
            assertThat(trip.getArrivedAtPickupAt()).isEqualTo(NOW);

            trip.pickUpCustomer(true, NOW.plusSeconds(120));
            assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
            assertThat(trip.getIdentityVerifiedAt()).isEqualTo(NOW.plusSeconds(120));

            trip.complete(BigDecimal.valueOf(120_000), "VND");
            assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
            assertThat(trip.getFinalFareAmount()).isEqualByComparingTo("120000");
            assertThat(trip.getFinalFareCurrency()).isEqualTo("VND");
            assertThat(trip.isTerminal()).isTrue();
        }
    }

    @Nested
    class InvalidTransitions {

        @Test
        void cannotPickUpCustomerBeforeArriving() {
            var trip = newTrip();
            assertThatThrownBy(() -> trip.pickUpCustomer(true, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("STARTED");
        }

        /** CLAUDE.md C.2 — skipping the identity check is exactly the "lái xe không phép" exposure the edge case warns about; the state machine refuses to proceed without it. */
        @Test
        void refusesToHandOverTheWheelWithoutIdentityVerification() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);

            assertThatThrownBy(() -> trip.pickUpCustomer(false, NOW.plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("xác minh");
            assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED_AT_PICKUP);
            assertThat(trip.getIdentityVerifiedAt()).isNull();
        }

        @Test
        void cannotCompleteBeforeDriverHasTheWheel() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);
            assertThatThrownBy(() -> trip.complete(BigDecimal.TEN, "VND"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotSkipStraightFromStartedToInProgress() {
            var trip = newTrip();
            // arriveAtPickup is the only legal exit from STARTED — pickUpCustomer requires ARRIVED_AT_PICKUP first.
            assertThatThrownBy(() -> trip.pickUpCustomer(true, NOW)).isInstanceOf(IllegalStateException.class);
            assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTED);
        }

        @Test
        void cannotArriveTwice() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);
            assertThatThrownBy(() -> trip.arriveAtPickup(NOW.plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotChangeDestinationBeforeDriverHasTheWheel() {
            var trip = newTrip();
            assertThatThrownBy(() -> trip.changeDestination(11.0, 107.0))
                    .isInstanceOf(IllegalStateException.class);

            trip.arriveAtPickup(NOW);
            assertThatThrownBy(() -> trip.changeDestination(11.0, 107.0))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void changeDestinationReturnsThePreviousCoordinatesForAuditLogging() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);
            trip.pickUpCustomer(true, NOW.plusSeconds(60));

            var previous = trip.changeDestination(11.0, 107.0);

            assertThat(previous.latitude()).isEqualTo(10.05);
            assertThat(previous.longitude()).isEqualTo(106.05);
            assertThat(trip.getDestinationLatitude()).isEqualTo(11.0);
            assertThat(trip.getDestinationLongitude()).isEqualTo(107.0);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void canCancelFromAnyNonTerminalStatus() {
            var enRoute = newTrip();
            enRoute.cancel(CancellationReason.CUSTOMER_REQUEST);
            assertThat(enRoute.getStatus()).isEqualTo(TripStatus.CANCELLED);
            assertThat(enRoute.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);

            var midTrip = newTrip();
            midTrip.arriveAtPickup(NOW);
            midTrip.pickUpCustomer(true, NOW.plusSeconds(60));
            midTrip.cancel(CancellationReason.FORCE_MAJEURE);
            assertThat(midTrip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        }

        @Test
        void cannotCancelATerminalTrip() {
            var trip = newTrip();
            trip.cancel(CancellationReason.CUSTOMER_REQUEST);
            assertThatThrownBy(() -> trip.cancel(CancellationReason.DRIVER_REQUEST))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /** CLAUDE.md C.1 — the no-show clock starts at arrival and the customer is owed the full grace window. */
    @Nested
    class NoShow {

        private static final Duration GRACE = Duration.ofMinutes(10);

        @Test
        void cannotDeclareNoShowBeforeArriving() {
            var trip = newTrip();
            assertThatThrownBy(() -> trip.cancelNoShow(NOW.plus(GRACE), GRACE))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotDeclareNoShowBeforeGracePeriodElapses() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);

            assertThatThrownBy(() -> trip.cancelNoShow(NOW.plus(GRACE).minusSeconds(1), GRACE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("grace");
            assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED_AT_PICKUP);
        }

        @Test
        void declaresNoShowOnceGracePeriodHasFullyElapsed() {
            var trip = newTrip();
            trip.arriveAtPickup(NOW);

            trip.cancelNoShow(NOW.plus(GRACE), GRACE);

            assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
            assertThat(trip.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_NO_SHOW);
            assertThat(trip.isTerminal()).isTrue();
        }
    }
}
