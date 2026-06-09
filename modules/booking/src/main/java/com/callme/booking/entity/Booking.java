package com.callme.booking.entity;

import com.callme.common.shared.CancellationReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(force = true)
public class Booking {

    /**
     * CLAUDE.md E.1 — "hủy trong 1 phút đầu vẫn miễn phí". Anchored at
     * {@link #confirmedAt} (driver assigned, already travelling toward pickup) rather
     * than booking creation, because that's the moment the company actually starts
     * incurring a cost worth recovering — backing out before a driver is even matched
     * costs nobody anything (CLAUDE.md A.5, still unconditionally free below).
     */
    public static final Duration CANCELLATION_GRACE_PERIOD = Duration.ofMinutes(1);

    /**
     * CLAUDE.md E.1 — flat, not proportional to the fare: what's being compensated is
     * the driver's wasted trip to the pickup point, a cost that's roughly constant
     * regardless of how far/expensive the ride itself would have been.
     */
    public static final BigDecimal CANCELLATION_FEE_AMOUNT = new BigDecimal("20000");
    public static final String CANCELLATION_FEE_CURRENCY = "VND";

    @Id
    @GeneratedValue
    private UUID id;

    private UUID customerId;

    private double pickupLatitude;

    private double pickupLongitude;

    private double destinationLatitude;

    private double destinationLongitude;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private UUID assignedDriverId;

    /** CLAUDE.md E.1 — when a driver was matched; anchors the cancellation-fee grace period. Null until {@link #confirmWithDriver}. */
    private Instant confirmedAt;

    private BigDecimal estimatedFareAmount;

    private String estimatedFareCurrency;

    /**
     * Client-supplied de-duplication token (CLAUDE.md A.3 — "đặt trùng lặp do mạng
     * chập chờn"). Unique per customer (DB constraint above) so a retried request
     * with the same key can never create a second booking — see
     * {@code BookingRepository.findByCustomerIdAndIdempotencyKey}. Null for clients
     * that don't send one; Postgres treats distinct NULLs as non-conflicting.
     */
    private String idempotencyKey;

    /** CLAUDE.md E.3 — recorded at cancellation time so a later fee/dispute policy can tell who (if anyone) was at fault. Null until cancelled. */
    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    /**
     * CLAUDE.md E.1 — set only when the customer backs out of an already-matched ride
     * past {@link #CANCELLATION_GRACE_PERIOD}; null for every fee-exempt cancellation
     * (no driver yet, driver/system/force-majeure-attributed, ...). A flat amount
     * recorded here — not charged through {@code Payment}, which is wired 1:1 to a
     * completed trip's fare — leaves a clear, queryable trail for CSKH/đối soát to
     * collect against without inventing a parallel charge pathway for the rare case.
     */
    private BigDecimal cancellationFeeAmount;

    private String cancellationFeeCurrency;

    /**
     * Optimistic lock (CLAUDE.md G.1) — guards against the customer cancelling in
     * the same instant dispatch confirms a driver, which would otherwise silently
     * overwrite one another's status transition.
     */
    @Version
    private long version;

    public Booking(UUID customerId,
                   double pickupLatitude, double pickupLongitude,
                   double destinationLatitude, double destinationLongitude,
                   BigDecimal estimatedFareAmount, String estimatedFareCurrency,
                   String idempotencyKey) {
        this.customerId = customerId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.estimatedFareAmount = estimatedFareAmount;
        this.estimatedFareCurrency = estimatedFareCurrency;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING;
    }

    public void confirmWithDriver(UUID driverId, Instant now) {
        if (this.status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot assign a driver to a cancelled booking: " + this.id);
        }
        this.assignedDriverId = driverId;
        this.status = BookingStatus.CONFIRMED;
        // CLAUDE.md E.1 — anchors the cancellation-fee grace period; `now` is threaded
        // through (rather than read via Instant.now() here) so the policy in #cancel
        // can be exercised deterministically in tests, the same way Rating#edit does.
        this.confirmedAt = now;
    }

    /**
     * No driver was within range at request time (CLAUDE.md A.1). Distinct from
     * PENDING so the customer sees a clear "không tìm thấy tài xế" outcome and can
     * decide to retry, rather than waiting indefinitely on a request nothing is
     * actively working on (this skeleton has no background re-matching scheduler yet).
     */
    public void markNoDriverFound() {
        this.status = BookingStatus.NO_DRIVER_FOUND;
    }

    /**
     * Customer-initiated cancellation (CLAUDE.md edge case E.1). Once a trip has
     * actually started the driver is holding the customer's car — cancellation at
     * that point is the trip module's concern (handle-with-care flow), not booking's.
     */
    public void cancel(CancellationReason reason, Instant now) {
        if (this.status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking already cancelled: " + this.id);
        }
        // CLAUDE.md E.1 — a fee only ever applies to a customer backing out of a ride
        // a driver is already travelling toward, and only past the grace period: a
        // PENDING/NO_DRIVER_FOUND booking never cost the company anything (CLAUDE.md
        // A.5 — still unconditionally free), and every other reason already excuses
        // the customer by definition (driver/system/force-majeure/no-show/breakdown...).
        if (reason == CancellationReason.CUSTOMER_REQUEST
                && this.status == BookingStatus.CONFIRMED
                && this.confirmedAt != null
                && Duration.between(this.confirmedAt, now).compareTo(CANCELLATION_GRACE_PERIOD) > 0) {
            this.cancellationFeeAmount = CANCELLATION_FEE_AMOUNT;
            this.cancellationFeeCurrency = CANCELLATION_FEE_CURRENCY;
        }
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
    }
}
