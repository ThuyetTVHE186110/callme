package com.callme;

import com.callme.booking.dto.CreateBookingRequest;
import com.callme.booking.entity.BookingStatus;
import com.callme.booking.service.BookingService;
import com.callme.common.exception.ConflictException;
import com.callme.common.exception.ForbiddenException;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.driver.entity.BackgroundCheckStatus;
import com.callme.driver.entity.Driver;
import com.callme.driver.repository.DriverRepository;
import com.callme.identity.entity.Customer;
import com.callme.identity.repository.CustomerRepository;
import com.callme.payment.entity.PaymentMethod;
import com.callme.payment.entity.PaymentStatus;
import com.callme.payment.service.PaymentService;
import com.callme.trip.entity.TripStatus;
import com.callme.trip.repository.TripRepository;
import com.callme.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end happy-path test for the booking → trip → payment lifecycle, running
 * against a real PostgreSQL instance (via Testcontainers — see
 * {@link AbstractIntegrationTest}) with all 10 Flyway migrations applied.
 *
 * <p>Exercises the full dispatch chain ({@code DriverMatchingPortImpl},
 * {@code DriverAvailabilityPortImpl}, {@code FareEstimationPortImpl}) and all
 * cross-module event listeners ({@code BookingEventListener} creating the Trip,
 * {@code TripCompletedEventListener} creating the Payment) that a unit test with
 * mocked ports cannot reach — this is exactly the class of bug that killed the
 * mocked-DB approach referenced in CLAUDE.md G.1.
 */
class BookingLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final double PICKUP_LAT = 10.776;
    private static final double PICKUP_LNG = 106.701;
    private static final double DEST_LAT   = 10.762;
    private static final double DEST_LNG   = 106.660;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private DriverRepository   driverRepository;
    @Autowired private BookingService     bookingService;
    @Autowired private TripRepository     tripRepository;
    @Autowired private TripService        tripService;
    @Autowired private PaymentService     paymentService;

    @Test
    @Transactional
    void fullLifecycle_bookingToCompletedPayment() {
        // --- Arrange: a customer and a nearby, available driver ---
        var customer = customerRepository.save(new Customer("Nguyễn Văn A", "0901234567", "vana@example.com"));
        var customerPrincipal = new AuthenticatedAccount(UUID.randomUUID(), customer.getId(), AccountRole.CUSTOMER);

        var driver = new Driver("Trần Văn B");
        var now = Instant.now();
        driver.recordVerification(BackgroundCheckStatus.APPROVED, LocalDate.now().plusYears(1), LocalDate.now().plusYears(1), now);
        driver.goOnline(now);
        driver.updateLocation(PICKUP_LAT, PICKUP_LNG, now);
        driver = driverRepository.save(driver);
        var driverPrincipal = new AuthenticatedAccount(UUID.randomUUID(), driver.getId(), AccountRole.DRIVER);

        // --- Act 1: create booking — synchronously matches driver, creates Trip ---
        var request = new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, null);
        UUID bookingId = bookingService.requestDriverHome(customer.getId(), request, "idem-" + UUID.randomUUID());

        // --- Assert: a Trip is created in STARTED status via BookingConfirmedEvent ---
        var trip = tripRepository
                .findFirstByBookingIdAndStatusInOrderByIdDesc(bookingId, List.of(TripStatus.STARTED))
                .orElseThrow(() -> new AssertionError("Expected a STARTED Trip after booking confirmation"));
        assertThat(trip.getDriverId()).isEqualTo(driver.getId());
        assertThat(trip.getCustomerId()).isEqualTo(customer.getId());

        UUID tripId = trip.getId();

        // --- Act 2: driver arrives at pickup ---
        tripService.arriveAtPickup(tripId, driverPrincipal);
        assertThat(tripRepository.findById(tripId)).hasValueSatisfying(
                t -> assertThat(t.getStatus()).isEqualTo(TripStatus.ARRIVED_AT_PICKUP));

        // --- Act 3: driver verifies identity and takes the wheel ---
        tripService.pickUpCustomer(tripId, driverPrincipal, true);
        assertThat(tripRepository.findById(tripId)).hasValueSatisfying(
                t -> assertThat(t.getStatus()).isEqualTo(TripStatus.IN_PROGRESS));

        // --- Act 4: driver delivers customer — fare is re-quoted, TripCompletedEvent
        //     fires, and TripCompletedEventListener creates a Payment(PENDING, CASH). ---
        tripService.complete(tripId, driverPrincipal);
        assertThat(tripRepository.findById(tripId)).hasValueSatisfying(
                t -> assertThat(t.getStatus()).isEqualTo(TripStatus.COMPLETED));

        // --- Assert: a Payment record exists for this trip, visible to BOTH parties
        //     (the driver is the one collecting cash — CLAUDE.md D.1) ---
        var payment = paymentService.getByTrip(tripId, customerPrincipal);
        assertThat(payment).isNotNull();
        assertThat(payment.amount()).isPositive();
        assertThat(paymentService.getByTrip(tripId, driverPrincipal).driverId()).isEqualTo(driver.getId());

        // --- Act 5: customer hands over cash; the DRIVER (the party receiving the
        //     money) attests the settlement — CLAUDE.md D.1/§4.3 ---
        paymentService.confirm(payment.id(), PaymentMethod.CASH, driverPrincipal);

        // --- Assert: Payment is CONFIRMED ---
        var confirmed = paymentService.getByTrip(tripId, customerPrincipal);
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.COMPLETED);

        // --- Assert: the booking settled as COMPLETED in lockstep with its trip —
        //     without this it would sit in CONFIRMED forever and the A.6
        //     one-active-booking rule would lock the customer out for good. ---
        var settledBooking = bookingService.get(bookingId, customerPrincipal);
        assertThat(settledBooking.status()).isEqualTo(BookingStatus.COMPLETED);

        // --- Act 6: the same customer books again — the true regression test for the
        //     "first ride permanently blocks the account" bug. ---
        UUID secondBookingId = bookingService.requestDriverHome(customer.getId(),
                new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, null),
                "idem-" + UUID.randomUUID());
        assertThat(secondBookingId).isNotEqualTo(bookingId);
    }

    /**
     * CLAUDE.md E.2/D — once the driver is behind the wheel of the customer's car,
     * neither the trip door nor the booking door may let the customer "cancel": a
     * mid-route cancel would erase the entire fare (no TripCompletedEvent → no
     * Payment) — ride 19 of 20 km, cancel, walk away free. Ending early legitimately
     * is changeDestination + driver complete.
     */
    @Test
    @Transactional
    void customerCannotCancelOutFromUnderAnInProgressTrip() {
        var customer = customerRepository.save(new Customer("Nguyễn Văn C", "0907654321", "vanc@example.com"));
        var customerPrincipal = new AuthenticatedAccount(UUID.randomUUID(), customer.getId(), AccountRole.CUSTOMER);

        var driver = new Driver("Trần Văn D");
        var now = Instant.now();
        driver.recordVerification(BackgroundCheckStatus.APPROVED, LocalDate.now().plusYears(1), LocalDate.now().plusYears(1), now);
        driver.goOnline(now);
        driver.updateLocation(PICKUP_LAT, PICKUP_LNG, now);
        driver = driverRepository.save(driver);
        var driverPrincipal = new AuthenticatedAccount(UUID.randomUUID(), driver.getId(), AccountRole.DRIVER);

        UUID bookingId = bookingService.requestDriverHome(customer.getId(),
                new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, null),
                "idem-" + UUID.randomUUID());
        UUID tripId = tripRepository
                .findFirstByBookingIdAndStatusInOrderByIdDesc(bookingId, List.of(TripStatus.STARTED))
                .orElseThrow().getId();
        tripService.arriveAtPickup(tripId, driverPrincipal);
        tripService.pickUpCustomer(tripId, driverPrincipal, true);

        // Trip door: refused outright.
        assertThatThrownBy(() -> tripService.cancel(tripId, customerPrincipal))
                .isInstanceOf(ForbiddenException.class);

        // Booking door: the cascade guard vetoes it (and rolls the booking's own
        // cancellation back atomically — G.1).
        assertThatThrownBy(() -> bookingService.cancel(bookingId, customerPrincipal))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * CLAUDE.md A.6 + §4.7 — an advance booking for tomorrow night must not lock the
     * customer out of riding home right now (it isn't "in a car"), but a second
     * advance booking is still one too many.
     */
    @Test
    @Transactional
    void anAdvanceBookingDoesNotBlockAnImmediateRide() {
        var customer = customerRepository.save(new Customer("Nguyễn Văn E", "0909999888", "vane@example.com"));

        var driver = new Driver("Trần Văn F");
        var now = Instant.now();
        driver.recordVerification(BackgroundCheckStatus.APPROVED, LocalDate.now().plusYears(1), LocalDate.now().plusYears(1), now);
        driver.goOnline(now);
        driver.updateLocation(PICKUP_LAT, PICKUP_LNG, now);
        driverRepository.save(driver);

        // Advance booking for tomorrow night — stays PENDING, no driver reserved (§4.7).
        UUID scheduledId = bookingService.requestDriverHome(customer.getId(),
                new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, now.plus(java.time.Duration.ofHours(24))),
                "idem-" + UUID.randomUUID());
        assertThat(scheduledId).isNotNull();

        // The customer can still ride home right now.
        UUID immediateId = bookingService.requestDriverHome(customer.getId(),
                new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, null),
                "idem-" + UUID.randomUUID());
        assertThat(immediateId).isNotEqualTo(scheduledId);

        // But a second advance booking is refused — one at a time.
        assertThatThrownBy(() -> bookingService.requestDriverHome(customer.getId(),
                new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, now.plus(java.time.Duration.ofHours(30))),
                "idem-" + UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }
}
