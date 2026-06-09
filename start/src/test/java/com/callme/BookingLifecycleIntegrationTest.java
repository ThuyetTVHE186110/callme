package com.callme;

import com.callme.booking.dto.CreateBookingRequest;
import com.callme.booking.service.BookingService;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        driver.goOnline();
        driver.updateLocation(PICKUP_LAT, PICKUP_LNG, Instant.now());
        driver = driverRepository.save(driver);
        var driverPrincipal = new AuthenticatedAccount(UUID.randomUUID(), driver.getId(), AccountRole.DRIVER);

        // --- Act 1: create booking — synchronously matches driver, creates Trip ---
        var request = new CreateBookingRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG);
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

        // --- Assert: a Payment record exists for this trip ---
        var payment = paymentService.getByTrip(tripId, customerPrincipal);
        assertThat(payment).isNotNull();
        assertThat(payment.amount()).isPositive();

        // --- Act 5: customer pays cash on the spot ---
        paymentService.confirm(payment.id(), PaymentMethod.CASH, customerPrincipal);

        // --- Assert: Payment is CONFIRMED ---
        var confirmed = paymentService.getByTrip(tripId, customerPrincipal);
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
