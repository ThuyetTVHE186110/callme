package com.callme.booking.service;

import com.callme.booking.dto.BookingResponse;
import com.callme.booking.dto.CreateBookingRequest;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.CancellationReason;

import java.util.UUID;

public interface BookingService {

    /**
     * @param idempotencyKey optional client-supplied de-duplication token (CLAUDE.md A.3).
     *                       A retry with the same key for the same customer replays the
     *                       original booking's id rather than creating a duplicate.
     */
    UUID requestDriverHome(UUID customerId, CreateBookingRequest request, String idempotencyKey);

    BookingResponse get(UUID bookingId, AuthenticatedAccount requester);

    void cancel(UUID bookingId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md B.4 — the previously-assigned driver backed out before reaching the
     * customer. The booking must NOT be cancelled: re-run matching for a replacement
     * and keep the customer's original request alive. No-ops if the booking is no
     * longer CONFIRMED (e.g. the customer cancelled in the same window — nothing to
     * re-dispatch for).
     *
     * @param withdrawnDriverId the driver who just backed out — excluded from this
     *                          re-match so the customer is never handed straight back
     *                          to the very driver who abandoned them (CLAUDE.md B.3/B.4);
     *                          they remain matchable for other, future requests.
     */
    void rematchAfterDriverWithdrawal(UUID bookingId, UUID withdrawnDriverId);

    /**
     * Reacts to {@code TripCompletedEvent} — the ride happened, so the booking is
     * settled as COMPLETED. Without this the booking would sit in CONFIRMED forever,
     * permanently blocking the customer's next request via the one-active-booking
     * rule (CLAUDE.md A.6). Idempotent: no-ops if the booking already left CONFIRMED.
     */
    void completeForTrip(UUID bookingId);

    /**
     * Reacts to {@code TripCancelledEvent} — closes the booking in lockstep with its
     * trip, classified by the trip's own cancellation reason so the E.1/E.3 fee policy
     * in {@code Booking#cancel} is applied exactly once, in one place. Skips the
     * reasons that must NOT end the booking: {@code SYSTEM_CASCADE} (the booking
     * itself initiated the cascade) and {@code DRIVER_REQUEST}/{@code DRIVER_UNRESPONSIVE}
     * (CLAUDE.md B.4 — the request survives and is re-dispatched via
     * {@link #rematchAfterDriverWithdrawal}).
     */
    void closeAfterTripCancellation(UUID bookingId, CancellationReason reason);
}
