package com.callme.booking.service.impl;

import com.callme.booking.dto.BookingResponse;
import com.callme.booking.dto.CreateBookingRequest;
import com.callme.booking.entity.Booking;
import com.callme.booking.entity.BookingStatus;
import com.callme.booking.repository.BookingRepository;
import com.callme.booking.service.BookingService;
import com.callme.common.event.BookingCancelledEvent;
import com.callme.common.event.BookingConfirmedEvent;
import com.callme.common.exception.ConflictException;
import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.port.DriverMatchingPort;
import com.callme.common.port.DriverReservationPort;
import com.callme.common.port.FareEstimationPort;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.CancellationReason;
import com.callme.common.shared.GeoPoint;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private static final double SEARCH_RADIUS_KM = 5.0;

    /**
     * CLAUDE.md A.6 — "khách đặt nhiều chuyến cùng lúc": a customer is one person in
     * one car at a time, so more than one PENDING/CONFIRMED booking simultaneously is
     * always either a mistake or abuse, never a legitimate need. NO_DRIVER_FOUND and
     * CANCELLED don't count — those are dead ends the customer must be free to retry from.
     */
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final DriverMatchingPort driverMatchingPort;
    private final DriverReservationPort driverReservationPort;
    private final FareEstimationPort fareEstimationPort;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;

    /**
     * `driverMatchingPort`, `driverReservationPort` and `fareEstimationPort` are `common`
     * contracts — Spring wires the `dispatch`/`driver`/`pricing` modules' implementations
     * in at runtime even though this module has no compile-time dependency on any of them.
     */
    public BookingServiceImpl(DriverMatchingPort driverMatchingPort,
                              DriverReservationPort driverReservationPort,
                              FareEstimationPort fareEstimationPort,
                              BookingRepository bookingRepository,
                              ApplicationEventPublisher events) {
        this.driverMatchingPort = driverMatchingPort;
        this.driverReservationPort = driverReservationPort;
        this.fareEstimationPort = fareEstimationPort;
        this.bookingRepository = bookingRepository;
        this.events = events;
    }

    /**
     * Overrides the class-level {@code @Transactional} to prevent the
     * {@code DataIntegrityViolationException} (duplicate idempotency key) catch block
     * from marking the transaction rollback-only before we've had a chance to handle
     * it — the catch releases the reserved driver and either returns the winning replay
     * or rethrows. Reads inside the catch can still fail if Hibernate's session was
     * poisoned by the constraint violation; in that case the exception propagates as a
     * 409 and the client retries (the upfront idempotency lookup handles the sequential
     * case, so this code path is only the concurrent-request safety net).
     */
    @Override
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public UUID requestDriverHome(UUID customerId, CreateBookingRequest request, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);

        // CLAUDE.md A.3 — a flaky-network retry of the exact same request must replay
        // the original booking, not spin up a second driver search/assignment.
        if (key != null) {
            var existing = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, key);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        // CLAUDE.md A.6 — block a second simultaneous booking outright; checked after the
        // idempotency replay above so a flaky-network retry of the SAME request never
        // gets misread as "another active booking" and rejected.
        if (bookingRepository.existsByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES)) {
            throw new ConflictException("Bạn đang có một chuyến đặt xe đang hoạt động — vui lòng hoàn tất hoặc huỷ chuyến đó trước khi đặt thêm");
        }

        var pickup = new GeoPoint(request.pickupLatitude(), request.pickupLongitude());
        var destination = new GeoPoint(request.destinationLatitude(), request.destinationLongitude());

        // CLAUDE.md §4.4 — quote at the moment the customer is asking, so a 1 a.m.
        // request shows the night surcharge up front rather than surprising them later.
        var quote = fareEstimationPort.estimate(pickup, destination, Instant.now());
        var booking = new Booking(customerId,
                pickup.latitude(), pickup.longitude(),
                destination.latitude(), destination.longitude(),
                quote.amount().amount(), quote.amount().currency().getCurrencyCode(),
                key);

        // matchDriver already atomically *reserves* the chosen candidate (CLAUDE.md B.1) —
        // by the time we get here the driver is taken out of the pool, so if anything
        // below fails we must release them or they'd be stranded "on trip" forever (no
        // trip will ever be created for them).
        var matchedDriver = driverMatchingPort.matchDriver(pickup, SEARCH_RADIUS_KM);
        if (matchedDriver.isPresent()) {
            booking.confirmWithDriver(matchedDriver.get(), Instant.now());
        } else {
            // CLAUDE.md A.1 — surface "no driver available" explicitly instead of
            // leaving the booking stuck in PENDING with no feedback to the customer.
            booking.markNoDriverFound();
        }

        try {
            bookingRepository.save(booking);
        } catch (DataIntegrityViolationException duplicateKey) {
            // Postgres treats distinct NULLs as non-conflicting, so this can only fire
            // when `key` is non-null: a concurrent retry with the same key won the
            // race and inserted first (the upfront lookup above can't see uncommitted
            // rows — the DB constraint is the final authority). Discard this attempt
            // and replay the winner instead of surfacing a confusing 500 to the client.
            matchedDriver.ifPresent(driverReservationPort::release);
            if (key != null) {
                var winner = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, key);
                if (winner.isPresent()) {
                    return winner.get().getId();
                }
            }
            throw duplicateKey;
        } catch (RuntimeException e) {
            matchedDriver.ifPresent(driverReservationPort::release);
            throw e;
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            try {
                events.publishEvent(new BookingConfirmedEvent(booking.getId(), booking.getCustomerId(), booking.getAssignedDriverId(), pickup, destination));
            } catch (RuntimeException e) {
                matchedDriver.ifPresent(driverReservationPort::release);
                throw e;
            }
        }

        return booking.getId();
    }

    /**
     * CLAUDE.md B.4 — re-runs matching from scratch for a booking whose previously
     * assigned driver backed out before pickup. Mirrors the match → reserve → assign
     * → persist → publish sequence in {@link #requestDriverHome}, including releasing
     * the freshly-reserved driver if anything downstream fails — but starting from an
     * existing booking rather than a brand-new one, so there's no idempotency/duplicate-key
     * concern here.
     */
    @Override
    public void rematchAfterDriverWithdrawal(UUID bookingId) {
        var booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED) {
            // Customer already cancelled (or some other terminal transition raced us in) —
            // there is no live request left to re-dispatch.
            return;
        }

        var pickup = new GeoPoint(booking.getPickupLatitude(), booking.getPickupLongitude());
        var destination = new GeoPoint(booking.getDestinationLatitude(), booking.getDestinationLongitude());

        var matchedDriver = driverMatchingPort.matchDriver(pickup, SEARCH_RADIUS_KM);
        if (matchedDriver.isPresent()) {
            booking.confirmWithDriver(matchedDriver.get(), Instant.now());
        } else {
            // No replacement nearby — surface the same explicit "no driver" outcome as
            // a fresh request would (CLAUDE.md A.1) rather than leaving the customer
            // staring at a CONFIRMED booking whose driver silently vanished.
            booking.markNoDriverFound();
        }

        try {
            bookingRepository.save(booking);
        } catch (RuntimeException e) {
            matchedDriver.ifPresent(driverReservationPort::release);
            throw e;
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            try {
                events.publishEvent(new BookingConfirmedEvent(booking.getId(), booking.getCustomerId(), booking.getAssignedDriverId(), pickup, destination));
            } catch (RuntimeException e) {
                matchedDriver.ifPresent(driverReservationPort::release);
                throw e;
            }
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    @Override
    public BookingResponse get(UUID bookingId, AuthenticatedAccount requester) {
        var booking = findOrThrow(bookingId);
        requireParticipant(booking, requester);
        return toResponse(booking);
    }

    @Override
    public void cancel(UUID bookingId, AuthenticatedAccount requester) {
        var booking = findOrThrow(bookingId);
        if (!requester.isAdmin() && !requester.ownsProfile(booking.getCustomerId())) {
            throw new ForbiddenException("Chỉ khách hàng đã đặt mới có thể huỷ booking này");
        }
        // CLAUDE.md E.3 — classify by who is cancelling: a customer backing out is
        // simply CUSTOMER_REQUEST (a later fee policy may charge them); CSKH/admin only
        // ever steps in to cancel on someone's behalf for a legitimate exceptional
        // circumstance reported through support (accident, medical emergency...), so an
        // admin-initiated cancellation is recorded as FORCE_MAJEURE — the one lane through
        // which "bất khả kháng" enters the system without inviting customer self-declared abuse.
        var reason = requester.isAdmin() ? CancellationReason.FORCE_MAJEURE : CancellationReason.CUSTOMER_REQUEST;
        booking.cancel(reason, Instant.now());
        bookingRepository.save(booking);

        // A CONFIRMED booking already has a STARTED/IN_PROGRESS trip running in lockstep
        // (BookingConfirmedEvent triggers trip creation synchronously) — without this,
        // cancelling here would orphan that trip and strand its driver "on trip" forever.
        events.publishEvent(new BookingCancelledEvent(booking.getId()));
    }

    /** Either party to the booking — the requesting customer or the assigned driver — may view it; admins always may. */
    private void requireParticipant(Booking booking, AuthenticatedAccount requester) {
        if (requester.isAdmin()) {
            return;
        }
        boolean isOwner = requester.ownsProfile(booking.getCustomerId());
        boolean isAssignedDriver = booking.getAssignedDriverId() != null && requester.ownsProfile(booking.getAssignedDriverId());
        if (!isOwner && !isAssignedDriver) {
            throw new ForbiddenException("Bạn không có quyền xem booking này");
        }
    }

    private Booking findOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy booking: " + bookingId));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getCustomerId(), booking.getStatus(),
                booking.getAssignedDriverId(), booking.getEstimatedFareAmount(), booking.getEstimatedFareCurrency(),
                booking.getCancellationReason(), booking.getCancellationFeeAmount(), booking.getCancellationFeeCurrency());
    }
}
