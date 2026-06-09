package com.callme.trip.entity;

import com.callme.common.shared.CancellationReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(force = true)
public class Trip {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID bookingId;

    private UUID customerId;

    private UUID driverId;

    private double pickupLatitude;

    private double pickupLongitude;

    private double destinationLatitude;

    private double destinationLongitude;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    private BigDecimal finalFareAmount;

    private String finalFareCurrency;

    /**
     * CLAUDE.md §5 / C.1 — set when the driver reaches the pickup point (entering
     * {@link TripStatus#ARRIVED_AT_PICKUP}). Anchors both the no-show grace-period
     * timer and a future "phí chờ khách" calculation; null until the driver arrives.
     */
    private Instant arrivedAtPickupAt;

    /**
     * CLAUDE.md C.2 — "tài xế cần quy trình xác minh khách đúng là chủ xe... trước
     * khi nhận xe, tránh rủi ro pháp lý 'lái xe không phép'". Set only when the
     * driver explicitly attests they checked (see {@link #pickUpCustomer}); a null
     * value on an IN_PROGRESS+ trip is itself an audit red flag.
     */
    private Instant identityVerifiedAt;

    /** CLAUDE.md E.3 — recorded at cancellation time so a later fee/dispute policy can tell who (if anyone) was at fault. Null until cancelled. */
    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    /** CLAUDE.md B.3 — anchors the unresponsive-driver sweep: how long has this trip sat in STARTED without the driver reaching pickup? */
    private Instant createdAt;

    public Trip(UUID bookingId, UUID customerId, UUID driverId,
                double pickupLatitude, double pickupLongitude,
                double destinationLatitude, double destinationLongitude) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.status = TripStatus.STARTED;
        this.createdAt = Instant.now();
    }

    /**
     * Driver has reached the pickup point — but has NOT taken the wheel yet
     * (CLAUDE.md §5: "đã gặp khách, chuẩn bị lái" is its own step, not the same
     * moment as "đang đến điểm đón"). Records the arrival instant so a no-show
     * timer / waiting-fee calculation has something to anchor on.
     */
    public void arriveAtPickup(Instant at) {
        requireStatus(TripStatus.STARTED, "arrive at pickup");
        this.status = TripStatus.ARRIVED_AT_PICKUP;
        this.arrivedAtPickupAt = at;
    }

    /**
     * Driver has verified the customer/vehicle and taken the wheel of the customer's
     * own car (CLAUDE.md flow step 8 — the actual "đang lái xe khách" moment).
     *
     * CLAUDE.md C.2 — {@code identityVerified} is a deliberate, explicit attestation
     * from the driver, not a default-true convenience flag: skipping this check is
     * exactly the "lái xe không phép" legal exposure the edge case warns about, so
     * the state machine itself refuses to move forward without it on record.
     */
    public void pickUpCustomer(boolean identityVerified, Instant at) {
        requireStatus(TripStatus.ARRIVED_AT_PICKUP, "pick up customer");
        if (!identityVerified) {
            throw new IllegalStateException(
                    "Phải xác minh khách hàng đúng là chủ xe trước khi nhận xe — chuyến đi " + this.id);
        }
        this.identityVerifiedAt = at;
        this.status = TripStatus.IN_PROGRESS;
    }

    /**
     * CLAUDE.md C.1 — the customer never showed up at the pickup point. Only legal
     * once the driver has actually arrived AND the grace period has elapsed — neither
     * the driver nor the system gets to declare a no-show on a whim; the customer is
     * owed every second of the waiting window they were promised.
     */
    public void cancelNoShow(Instant now, Duration gracePeriod) {
        requireStatus(TripStatus.ARRIVED_AT_PICKUP, "cancel for no-show");
        Duration waited = Duration.between(this.arrivedAtPickupAt, now);
        if (waited.compareTo(gracePeriod) < 0) {
            throw new IllegalStateException("Cannot declare no-show on trip " + this.id
                    + " — only " + waited.toMinutes() + " of " + gracePeriod.toMinutes() + " grace minutes elapsed");
        }
        this.status = TripStatus.CANCELLED;
        this.cancellationReason = CancellationReason.CUSTOMER_NO_SHOW;
    }

    /**
     * CLAUDE.md C.5 — khách đổi điểm đến giữa chừng. Only legal once the driver is
     * actually behind the wheel (IN_PROGRESS); changing it earlier is just editing
     * the original request. Returns the previous destination so the caller can log
     * old → new for dispute resolution (CLAUDE.md D.2) before overwriting it here.
     */
    public GeoPointSnapshot changeDestination(double newLatitude, double newLongitude) {
        requireStatus(TripStatus.IN_PROGRESS, "change destination");
        var previous = new GeoPointSnapshot(this.destinationLatitude, this.destinationLongitude);
        this.destinationLatitude = newLatitude;
        this.destinationLongitude = newLongitude;
        return previous;
    }

    /** Plain old/new coordinate pair handed back to the caller for audit logging — avoids a dependency on `common` from the entity. */
    public record GeoPointSnapshot(double latitude, double longitude) {
    }

    public void complete(BigDecimal finalFareAmount, String finalFareCurrency) {
        requireStatus(TripStatus.IN_PROGRESS, "complete");
        this.finalFareAmount = finalFareAmount;
        this.finalFareCurrency = finalFareCurrency;
        this.status = TripStatus.COMPLETED;
    }

    /**
     * CLAUDE.md edge case E.2: cancelling once IN_PROGRESS means the driver is
     * physically holding the customer's car — this must only be reached through the
     * "bring car + customer to a safe stop first" support flow, never a casual abort.
     * The state machine still allows it (the alternative — getting permanently stuck
     * IN_PROGRESS — is worse), but callers must enforce that safety procedure upstream.
     */
    public void cancel(CancellationReason reason) {
        if (this.status == TripStatus.COMPLETED || this.status == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a trip in status " + this.status + ": " + this.id);
        }
        this.status = TripStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    /** CLAUDE.md C.6 — once a trip is COMPLETED/CANCELLED there's no active situation left for support to step into. */
    public boolean isTerminal() {
        return this.status == TripStatus.COMPLETED || this.status == TripStatus.CANCELLED;
    }

    private void requireStatus(TripStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " trip " + this.id + " from status " + this.status + " (expected " + expected + ")");
        }
    }
}
