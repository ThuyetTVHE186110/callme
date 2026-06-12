package com.callme.booking.entity;

import com.callme.common.shared.CancellationReason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md §2 invariant — "Một Booking chỉ được gán cho đúng một Driver tại một
 * thời điểm" — and edge cases A.1 (no driver found) / E.1 (cancellation). Pure
 * state-machine rules on the aggregate root; no Spring context required.
 */
class BookingTest {

    private static final Instant T0 = Instant.parse("2026-06-07T22:00:00Z");

    private Booking newBooking() {
        return new Booking(UUID.randomUUID(),
                10.0, 106.0, 10.05, 106.05,
                BigDecimal.valueOf(100_000), "VND", "idem-key-1", null, T0);
    }

    @Test
    void startsAsPendingWithNoAssignedDriver() {
        var booking = newBooking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getAssignedDriverId()).isNull();
    }

    @Nested
    class DriverMatching {

        @Test
        void confirmingWithADriverAssignsThemAndMovesToConfirmed() {
            var booking = newBooking();
            var driverId = UUID.randomUUID();

            booking.confirmWithDriver(driverId, T0);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getAssignedDriverId()).isEqualTo(driverId);
        }

        /** CLAUDE.md A.1 — no candidate driver was in range; the customer needs an explicit, distinguishable outcome. */
        @Test
        void noDriverFoundLeavesTheBookingWithoutAnAssignment() {
            var booking = newBooking();

            booking.markNoDriverFound();

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_DRIVER_FOUND);
            assertThat(booking.getAssignedDriverId()).isNull();
        }

        /** CLAUDE.md §2 invariant — a cancelled booking can never be handed to a driver after the fact (e.g. a race with a late "accept"). */
        @Test
        void cannotAssignADriverToACancelledBooking() {
            var booking = newBooking();
            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0);

            assertThatThrownBy(() -> booking.confirmWithDriver(UUID.randomUUID(), T0))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(booking.getAssignedDriverId()).isNull();
        }
    }

    @Nested
    class Cancellation {

        @Test
        void recordsTheReasonForLaterDisputeResolution() {
            var booking = newBooking();

            booking.cancel(CancellationReason.DRIVER_REQUEST, T0);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getCancellationReason()).isEqualTo(CancellationReason.DRIVER_REQUEST);
        }

        @Test
        void cannotCancelTwice() {
            var booking = newBooking();
            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0);

            assertThatThrownBy(() -> booking.cancel(CancellationReason.FORCE_MAJEURE, T0))
                    .isInstanceOf(IllegalStateException.class);
            // The original reason survives — a second cancel attempt must not overwrite the audit trail.
            assertThat(booking.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
        }

        @Test
        void aConfirmedBookingCanStillBeCancelled() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);

            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        }
    }

    /** CLAUDE.md A.6/flow 9→12 — a finished ride settles the booking; without COMPLETED the customer would be locked out of booking ever again. */
    @Nested
    class Completion {

        @Test
        void aConfirmedBookingCompletesWhenItsTripDoes() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);

            booking.complete();

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        }

        @Test
        void cannotCompleteABookingThatWasNeverConfirmed() {
            var booking = newBooking();

            assertThatThrownBy(booking::complete)
                    .isInstanceOf(IllegalStateException.class);
        }

        /** The ride happened — "cancelling" it after the fact would overwrite the completed outcome and could charge a bogus E.1 fee. */
        @Test
        void cannotCancelACompletedBooking() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);
            booking.complete();

            assertThatThrownBy(() -> booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0.plus(Booking.CANCELLATION_GRACE_PERIOD).plusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(booking.getCancellationFeeAmount()).isNull();
        }
    }

    /** CLAUDE.md E.1 — "khách hủy sau khi tài xế đã xác nhận và đang di chuyển đến điểm đón nên có phí hủy, nhưng cần ngưỡng thời gian hợp lý". */
    @Nested
    class CancellationFee {

        @Test
        void cancellingWithinTheGracePeriodIsFree() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);

            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0.plus(Booking.CANCELLATION_GRACE_PERIOD));

            assertThat(booking.getCancellationFeeAmount()).isNull();
            assertThat(booking.getCancellationFeeCurrency()).isNull();
        }

        @Test
        void cancellingPastTheGracePeriodChargesAFlatFee() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);

            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0.plus(Booking.CANCELLATION_GRACE_PERIOD).plusSeconds(1));

            assertThat(booking.getCancellationFeeAmount()).isEqualTo(Booking.CANCELLATION_FEE_AMOUNT);
            assertThat(booking.getCancellationFeeCurrency()).isEqualTo(Booking.CANCELLATION_FEE_CURRENCY);
        }

        /** CLAUDE.md A.5 — backing out before any driver is even matched must stay free, no matter how long the customer waited. */
        @Test
        void cancellingBeforeADriverIsMatchedIsAlwaysFree() {
            var booking = newBooking();

            booking.cancel(CancellationReason.CUSTOMER_REQUEST, T0.plus(Booking.CANCELLATION_GRACE_PERIOD).plusSeconds(1));

            assertThat(booking.getCancellationFeeAmount()).isNull();
        }

        /** CLAUDE.md B.4/E.3 — every non-customer-initiated reason already excuses the customer; never pile a fee on top. */
        @Test
        void onlyCustomerRequestedCancellationsCanCarryAFee() {
            var booking = newBooking();
            booking.confirmWithDriver(UUID.randomUUID(), T0);

            booking.cancel(CancellationReason.DRIVER_REQUEST, T0.plus(Booking.CANCELLATION_GRACE_PERIOD).plusSeconds(1));

            assertThat(booking.getCancellationFeeAmount()).isNull();
        }
    }

    /** CLAUDE.md §4.7 — "đặt lịch trước, giới hạn trong cửa sổ ngắn (tối đa 24-48 giờ)". */
    @Nested
    class AdvanceBooking {

        private Booking newBooking(Instant scheduledAt) {
            return new Booking(UUID.randomUUID(),
                    10.0, 106.0, 10.05, 106.05,
                    BigDecimal.valueOf(100_000), "VND", "idem-key-1", scheduledAt, T0);
        }

        @Test
        void anImmediateBookingIsAlwaysDueForMatching() {
            var booking = newBooking(null);
            assertThat(booking.isDueForMatching(T0)).isTrue();
        }

        @Test
        void rejectsAScheduledTimeInThePast() {
            assertThatThrownBy(() -> newBooking(T0.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAScheduledTimeAtOrBeforeNow() {
            assertThatThrownBy(() -> newBooking(T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAScheduledTimeBeyondTheAdvanceWindow() {
            var tooFar = T0.plus(Booking.MAX_ADVANCE_BOOKING_WINDOW).plusSeconds(1);
            assertThatThrownBy(() -> newBooking(tooFar))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsAScheduledTimeWithinTheAdvanceWindow() {
            var booking = newBooking(T0.plus(Booking.MAX_ADVANCE_BOOKING_WINDOW));
            assertThat(booking.getScheduledAt()).isEqualTo(T0.plus(Booking.MAX_ADVANCE_BOOKING_WINDOW));
        }

        @Test
        void aFarOutScheduledBookingIsNotYetDueForMatching() {
            var booking = newBooking(T0.plus(Booking.SCHEDULED_MATCH_LEAD_TIME).plusSeconds(1));
            assertThat(booking.isDueForMatching(T0)).isFalse();
        }

        @Test
        void aScheduledBookingWithinTheLeadTimeIsDueForMatching() {
            var booking = newBooking(T0.plus(Booking.SCHEDULED_MATCH_LEAD_TIME));
            assertThat(booking.isDueForMatching(T0)).isTrue();
        }
    }
}
