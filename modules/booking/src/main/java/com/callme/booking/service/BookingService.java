package com.callme.booking.service;

import com.callme.booking.dto.BookingResponse;
import com.callme.booking.dto.CreateBookingRequest;
import com.callme.common.security.AuthenticatedAccount;

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
     */
    void rematchAfterDriverWithdrawal(UUID bookingId);
}
