package com.callme.trip.service.impl;

import com.callme.common.event.DriverCancelledBeforePickupEvent;
import com.callme.common.event.DriverUnresponsiveEvent;
import com.callme.common.event.GpsSignalLostEvent;
import com.callme.common.event.SosRaisedEvent;
import com.callme.common.event.TripAbortedMidwayEvent;
import com.callme.common.event.TripCancelledEvent;
import com.callme.common.event.TripCompletedEvent;
import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.port.DriverLocationFreshnessPort;
import com.callme.common.port.DriverRouteTracePort;
import com.callme.common.port.FareEstimationPort;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.CancellationReason;
import com.callme.common.shared.GeoPoint;
import com.callme.common.shared.Money;
import com.callme.trip.dto.EmergencyAbortReportResponse;
import com.callme.trip.dto.RouteDeviationFlagResponse;
import com.callme.trip.dto.SosAlertResponse;
import com.callme.trip.dto.TripResponse;
import com.callme.trip.entity.EmergencyAbortReport;
import com.callme.trip.entity.RouteDeviationFlag;
import com.callme.trip.entity.SosAlert;
import com.callme.trip.entity.Trip;
import com.callme.trip.entity.TripStatus;
import com.callme.trip.repository.EmergencyAbortReportRepository;
import com.callme.trip.repository.RouteDeviationFlagRepository;
import com.callme.trip.repository.SosAlertRepository;
import com.callme.trip.repository.TripRepository;
import com.callme.trip.service.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TripServiceImpl implements TripService {

    private static final Logger log = LoggerFactory.getLogger(TripServiceImpl.class);

    /** Non-terminal trip statuses — at most one such row exists per booking at any time (see {@link TripRepository}). */
    private static final List<TripStatus> ACTIVE_TRIP_STATUSES =
            List.of(TripStatus.STARTED, TripStatus.ARRIVED_AT_PICKUP, TripStatus.IN_PROGRESS);

    /** CLAUDE.md C.1 — minimum wait at the pickup point before a no-show can be declared; the customer is owed every minute of it. */
    private static final Duration NO_SHOW_GRACE_PERIOD = Duration.ofMinutes(10);

    /**
     * CLAUDE.md B.3 — how long a matched driver gets to actually reach the pickup
     * point before being treated as unresponsive. There is no separate "accept/reject"
     * step in this system (matching reserves atomically — see {@code DriverMatchingPort}),
     * so this is the closest real analogue: a driver who was reserved but never showed
     * any sign of heading over. Generous on purpose — traffic and night-time conditions
     * are the norm in this domain, not the exception.
     */
    private static final Duration DRIVER_RESPONSE_TIMEOUT = Duration.ofMinutes(15);

    /** CLAUDE.md C.7 — beyond this age mid-trip, the driver's GPS trail is considered lost (safety-relevant: they're physically holding the customer's car). */
    private static final Duration GPS_LOSS_THRESHOLD = Duration.ofMinutes(3);

    /** Must match the {@code @Scheduled} cadence of {@link #sweepStaleGpsTrips} — defines the "just crossed the threshold" detection window so the alert fires exactly once, not every cycle. */
    private static final Duration GPS_SWEEP_INTERVAL = Duration.ofMinutes(1);

    /**
     * CLAUDE.md C.8 — how much longer the GPS-traced route may run than the
     * straight-line distance the fare was charged on before it's worth a support
     * look. 1.5× is deliberately generous: real streets are never straight lines,
     * so *some* excess is the norm, not the exception — this only catches the
     * unmistakably-large detours, leaving the borderline cases to human judgment.
     */
    private static final double ROUTE_DEVIATION_RATIO_THRESHOLD = 1.5;

    /**
     * CLAUDE.md C.8 — below this charged distance, GPS noise and equirectangular
     * approximation error dwarf any real detour signal; flagging short trips would
     * just be crying wolf at support. Skip the check entirely under this floor.
     */
    private static final double MIN_FLAGGABLE_DISTANCE_KM = 1.0;

    private final TripRepository tripRepository;
    private final SosAlertRepository sosAlertRepository;
    private final RouteDeviationFlagRepository routeDeviationFlagRepository;
    private final EmergencyAbortReportRepository emergencyAbortReportRepository;
    private final FareEstimationPort fareEstimationPort;
    private final DriverLocationFreshnessPort driverLocationFreshnessPort;
    private final DriverRouteTracePort driverRouteTracePort;
    private final ApplicationEventPublisher events;

    public TripServiceImpl(TripRepository tripRepository, SosAlertRepository sosAlertRepository,
                           RouteDeviationFlagRepository routeDeviationFlagRepository,
                           EmergencyAbortReportRepository emergencyAbortReportRepository,
                           FareEstimationPort fareEstimationPort, DriverLocationFreshnessPort driverLocationFreshnessPort,
                           DriverRouteTracePort driverRouteTracePort,
                           ApplicationEventPublisher events) {
        this.tripRepository = tripRepository;
        this.sosAlertRepository = sosAlertRepository;
        this.routeDeviationFlagRepository = routeDeviationFlagRepository;
        this.emergencyAbortReportRepository = emergencyAbortReportRepository;
        this.fareEstimationPort = fareEstimationPort;
        this.driverLocationFreshnessPort = driverLocationFreshnessPort;
        this.driverRouteTracePort = driverRouteTracePort;
        this.events = events;
    }

    @Override
    public UUID startTrip(UUID bookingId, UUID customerId, UUID driverId, GeoPoint pickup, GeoPoint destination) {
        var trip = new Trip(bookingId, customerId, driverId,
                pickup.latitude(), pickup.longitude(),
                destination.latitude(), destination.longitude());
        return tripRepository.save(trip).getId();
    }

    @Override
    public TripResponse get(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireParticipant(trip, requester);
        return toResponse(trip);
    }

    @Override
    public TripResponse getByBooking(UUID bookingId, AuthenticatedAccount requester) {
        var trip = tripRepository.findFirstByBookingIdOrderByIdDesc(bookingId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi cho booking: " + bookingId));
        requireParticipant(trip, requester);
        return toResponse(trip);
    }

    /**
     * CLAUDE.md §5 — only the assigned driver, physically there, can confirm they
     * reached the pickup point. This starts the no-show clock ({@link #cancelNoShow})
     * but does NOT hand them the wheel yet — see {@link #pickUpCustomer}.
     */
    @Override
    public void arriveAtPickup(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        trip.arriveAtPickup(Instant.now());
        tripRepository.save(trip);
    }

    /**
     * CLAUDE.md C.2 / flow step 8 — the driver has met the customer, verified they're
     * the registered owner, checked the vehicle, and is now taking the wheel. Only the
     * assigned driver can attest to this; it's the moment "lái xe hộ" actually begins.
     */
    @Override
    public void pickUpCustomer(UUID tripId, AuthenticatedAccount requester, boolean identityVerified) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        trip.pickUpCustomer(identityVerified, Instant.now());
        tripRepository.save(trip);
    }

    /**
     * CLAUDE.md C.1 — driver waited the full grace period at the pickup point and the
     * customer never appeared. Declared by the driver (the only one physically present
     * to know), gated by {@link Trip#cancelNoShow} on both the trip status AND the
     * elapsed wait — neither side can shortcut the clock. Goes through the same
     * cascade as any other trip-level cancellation so the booking and dispatch state
     * stay consistent (CLAUDE.md invariants in §2).
     */
    @Override
    public void cancelNoShow(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        trip.cancelNoShow(Instant.now(), NO_SHOW_GRACE_PERIOD);
        tripRepository.save(trip);
        log.info("Trip {} cancelled as customer no-show by driver {} after grace period", trip.getId(), trip.getDriverId());
        events.publishEvent(new TripCancelledEvent(trip.getId(), trip.getBookingId(), trip.getDriverId()));
    }

    /**
     * Re-quotes the fare from the actual pickup/destination recorded at trip start —
     * this is the "final cước" the customer is charged, which may differ from the
     * booking-time estimate (CLAUDE.md edge case D.2 — keep both figures for dispute resolution).
     * Only the assigned driver can mark a trip complete — they're the one physically present.
     */
    @Override
    public void complete(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);

        var pickup = new GeoPoint(trip.getPickupLatitude(), trip.getPickupLongitude());
        var destination = new GeoPoint(trip.getDestinationLatitude(), trip.getDestinationLongitude());
        var now = Instant.now();
        // CLAUDE.md §4.4 — charge at completion time: a trip that ran past midnight
        // is billed the night surcharge that actually applied while it was happening.
        var quote = fareEstimationPort.estimate(pickup, destination, now);
        Money finalFare = quote.amount();

        trip.complete(finalFare.amount(), finalFare.currency().getCurrencyCode());
        tripRepository.save(trip);

        checkRouteDeviation(trip, quote.distanceKm(), now);

        events.publishEvent(new TripCompletedEvent(trip.getId(), trip.getBookingId(), trip.getCustomerId(), trip.getDriverId(), finalFare));
    }

    /**
     * CLAUDE.md C.8 — "tài xế cố tình đi đường vòng để tăng cước". Replays the
     * driver's GPS trail for the actual driving window — from the moment they took
     * the wheel ({@link Trip#getIdentityVerifiedAt()}, CLAUDE.md C.2) to completion —
     * via {@link DriverRouteTracePort}, and compares the summed travelled distance
     * against the straight-line distance the fare was charged on. A large excess
     * raises a {@link RouteDeviationFlag} for support to review with the full trail
     * in hand; it is deliberately NOT auto-penalised (see the entity's javadoc for why).
     */
    private void checkRouteDeviation(Trip trip, double expectedDistanceKm, Instant completedAt) {
        if (expectedDistanceKm < MIN_FLAGGABLE_DISTANCE_KM || trip.getIdentityVerifiedAt() == null) {
            return;
        }
        double actualDistanceKm = driverRouteTracePort.cumulativeDistanceKm(trip.getDriverId(), trip.getIdentityVerifiedAt(), completedAt);
        double ratio = actualDistanceKm / expectedDistanceKm;
        if (ratio > ROUTE_DEVIATION_RATIO_THRESHOLD) {
            var flag = routeDeviationFlagRepository.save(
                    new RouteDeviationFlag(trip.getId(), trip.getDriverId(), expectedDistanceKm, actualDistanceKm, ratio, completedAt));
            log.warn("Trip {} flagged for route deviation — driver {} travelled {} km against an expected {} km (ratio {}); flag {} queued for support review",
                    trip.getId(), trip.getDriverId(), actualDistanceKm, expectedDistanceKm, ratio, flag.getId());
        }
    }

    /** Statuses in which the driver is actually with the customer's vehicle and could discover it won't run — CLAUDE.md C.3. */
    private static final List<TripStatus> WITH_VEHICLE_STATUSES = List.of(TripStatus.ARRIVED_AT_PICKUP, TripStatus.IN_PROGRESS);

    /**
     * CLAUDE.md C.3 — driver discovers the customer's car won't start or breaks down
     * mid-route. This is the customer's risk, not a dispute between participants, so
     * it skips the usual {@link #classifyCancellation} (which would otherwise blame
     * one of them) and goes straight to a dedicated, fee-neutral reason — support
     * picks it up from there for roadside assistance / switching to an ordinary taxi.
     */
    @Override
    public void reportVehicleBreakdown(UUID tripId, AuthenticatedAccount requester, String note) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        if (!WITH_VEHICLE_STATUSES.contains(trip.getStatus())) {
            throw new ForbiddenException(
                    "Không thể báo cáo xe hỏng khi tài xế chưa tiếp cận xe của khách — chuyến đi " + trip.getId() + " đang ở trạng thái " + trip.getStatus());
        }

        log.warn("Trip {} aborted — customer's vehicle broke down (reported by driver {}): {}", trip.getId(), trip.getDriverId(), note);
        cancelAndPublish(trip, CancellationReason.VEHICLE_BREAKDOWN);
    }

    /**
     * CLAUDE.md C.5 — only the riding customer can redirect the trip (the driver
     * doesn't get to decide where the customer's car ends up, and admins have no
     * legitimate reason to silently rewrite a route). Re-quoting immediately, rather
     * than waiting for {@link #complete}, means neither side is surprised by the
     * fare; logging old → new here — alongside the GPS trail already recorded by the
     * `location` module — is the dispute-resolution evidence trail CLAUDE.md calls for.
     */
    @Override
    public void changeDestination(UUID tripId, AuthenticatedAccount requester, GeoPoint newDestination) {
        var trip = findOrThrow(tripId);
        if (!requester.ownsProfile(trip.getCustomerId())) {
            throw new ForbiddenException("Chỉ khách hàng của chuyến đi mới có thể đổi điểm đến");
        }

        var previous = trip.changeDestination(newDestination.latitude(), newDestination.longitude());
        tripRepository.save(trip);

        var pickup = new GeoPoint(trip.getPickupLatitude(), trip.getPickupLongitude());
        var requote = fareEstimationPort.estimate(pickup, newDestination, Instant.now());
        log.info("Trip {} destination changed by customer {}: ({}, {}) -> ({}, {}); re-quoted fare {}",
                trip.getId(), trip.getCustomerId(),
                previous.latitude(), previous.longitude(),
                newDestination.latitude(), newDestination.longitude(),
                requote.amount());
    }

    /**
     * Either participant (customer or driver) may request cancellation; admins can
     * force it for dispute resolution. CLAUDE.md E.2 — the one carve-out: the assigned
     * driver may NOT use this to back out of an IN_PROGRESS trip. They are physically
     * holding the customer's car with the customer aboard; "bỏ chuyến" mid-route the
     * same way they'd cancel any other booking is exactly the casual escape hatch
     * CLAUDE.md warns must not exist. {@link #abortInProgressTrip} — which requires
     * first attesting the car and customer are already at a declared safe location —
     * is the only legitimate door out of that state for the driver.
     */
    @Override
    public void cancel(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireParticipant(trip, requester);
        if (trip.getStatus() == TripStatus.IN_PROGRESS && requester.ownsProfile(trip.getDriverId())) {
            throw new ForbiddenException(
                    "Tài xế không thể huỷ khi đang cầm lái xe của khách — phải đưa xe và khách đến nơi an toàn trước, qua quy trình báo cáo khẩn cấp giữa chuyến: chuyến đi " + trip.getId());
        }
        cancelAndPublish(trip, classifyCancellation(trip, requester));
    }

    /**
     * CLAUDE.md E.3 — classify by who actually requested the cancellation: the
     * customer or the driver bears CUSTOMER_REQUEST/DRIVER_REQUEST respectively
     * (a later fee policy may charge the requesting party); CSKH/admin only ever
     * intervenes here for a reported exceptional circumstance — accident, medical
     * emergency, natural disaster — so an admin-initiated cancel is the one lane
     * "bất khả kháng" enters the record without inviting self-declared abuse.
     */
    private CancellationReason classifyCancellation(Trip trip, AuthenticatedAccount requester) {
        if (requester.isAdmin()) {
            return CancellationReason.FORCE_MAJEURE;
        }
        return requester.ownsProfile(trip.getDriverId()) ? CancellationReason.DRIVER_REQUEST : CancellationReason.CUSTOMER_REQUEST;
    }

    /** Trip statuses in which the driver has not yet taken the wheel — backing out is still "before pickup", however far along the approach was. */
    private static final List<TripStatus> PRE_WHEEL_STATUSES = List.of(TripStatus.STARTED, TripStatus.ARRIVED_AT_PICKUP);

    /**
     * CLAUDE.md B.4 — gated to STARTED/ARRIVED_AT_PICKUP (not yet behind the wheel) so
     * a driver can never use this as an escape hatch once they're physically holding
     * the customer's car; that situation is IN_PROGRESS and must go through the
     * dedicated, safety-first {@link #abortInProgressTrip} flow (CLAUDE.md E.2), never
     * a casual "never mind, re-dispatch this" — even if they'd already arrived at the
     * pickup point.
     */
    @Override
    public void driverCancelBeforePickup(UUID tripId, AuthenticatedAccount requester) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        if (!PRE_WHEEL_STATUSES.contains(trip.getStatus())) {
            throw new ForbiddenException(
                    "Không thể huỷ theo cách này khi đã nhận xe của khách — chuyến đi " + trip.getId() + " đang ở trạng thái " + trip.getStatus());
        }

        UUID bookingId = trip.getBookingId();
        UUID driverId = trip.getDriverId();
        cancelAndPublish(trip, CancellationReason.DRIVER_REQUEST);

        // Distinct from a customer cancellation: the request must survive and be
        // handed to a replacement driver, not die with this trip.
        events.publishEvent(new DriverCancelledBeforePickupEvent(bookingId, tripId, driverId));
    }

    /**
     * CLAUDE.md E.2 — "tài xế phải đưa xe + khách đến nơi an toàn trước khi được phép
     * kết thúc bất thường". Gated to IN_PROGRESS (the one status where the driver
     * actually has the wheel — see {@link #cancel} for why every other exit funnels
     * through {@link #driverCancelBeforePickup} instead): {@code safeLocation} is the
     * driver's attestation, supplied at the moment of ending the trip, that the car
     * and customer are already parked there — not a request to be granted permission
     * to do so later. Cancels with the dedicated {@link CancellationReason#DRIVER_EMERGENCY_ABORT}
     * (a safety incident, never a casual bail or an ordinary fee dispute) and raises
     * an {@link EmergencyAbortReport} so CSKH can judge — case by case, the way C.3's
     * "gọi cứu hộ / đổi taxi thường" handoff already does — whether to send a
     * replacement driver to the declared spot.
     */
    @Override
    public void abortInProgressTrip(UUID tripId, AuthenticatedAccount requester, GeoPoint safeLocation, String note) {
        var trip = findOrThrow(tripId);
        requireAssignedDriver(trip, requester);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ForbiddenException(
                    "Chỉ có thể báo cáo kết thúc khẩn cấp giữa chuyến khi tài xế đang cầm lái xe của khách — chuyến đi " + trip.getId() + " đang ở trạng thái " + trip.getStatus());
        }

        UUID driverId = trip.getDriverId();
        UUID customerId = trip.getCustomerId();
        var now = Instant.now();
        cancelAndPublish(trip, CancellationReason.DRIVER_EMERGENCY_ABORT);

        var report = emergencyAbortReportRepository.save(new EmergencyAbortReport(
                tripId, driverId, customerId, safeLocation.latitude(), safeLocation.longitude(), note, now));
        log.warn("Trip {} aborted mid-route by driver {} after declaring safe location ({}, {}): {}",
                tripId, driverId, safeLocation.latitude(), safeLocation.longitude(), note);
        events.publishEvent(new TripAbortedMidwayEvent(report.getId(), tripId, customerId, driverId, safeLocation, now, note));
    }

    /** CSKH/admin worklist of mid-trip emergency aborts, most recent first — CLAUDE.md E.2 (mirrors {@link #listSosAlerts}). */
    @Override
    public List<EmergencyAbortReportResponse> listEmergencyAbortReports(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ quản trị viên mới có thể xem danh sách báo cáo khẩn cấp giữa chuyến");
        }
        return emergencyAbortReportRepository.findAllByOrderByReportedAtDesc().stream()
                .map(r -> new EmergencyAbortReportResponse(r.getId(), r.getTripId(), r.getDriverId(), r.getCustomerId(),
                        r.getSafeLocationLatitude(), r.getSafeLocationLongitude(), r.getNote(), r.getReportedAt()))
                .toList();
    }

    /**
     * Reacts to {@code BookingCancelledEvent} — without this, cancelling a CONFIRMED
     * booking would orphan its STARTED/IN_PROGRESS trip and strand the driver "on trip"
     * forever (only trip-ended events release them back into the matching pool).
     * Silently no-ops when there's nothing to do: no trip yet, or it already reached
     * a terminal state (e.g. the driver completed it moments before this arrived) —
     * {@link Trip#cancel} would otherwise throw IllegalStateException on a race like that.
     */
    @Override
    public void cancelForBooking(UUID bookingId) {
        tripRepository.findFirstByBookingIdAndStatusInOrderByIdDesc(bookingId, ACTIVE_TRIP_STATUSES)
                // CLAUDE.md E.3 — this trip didn't end because either participant chose to end
                // it; it's a side effect of its booking ending. Neither side should be charged
                // a cancellation fee for a consequence outside their control.
                .ifPresent(trip -> cancelAndPublish(trip, CancellationReason.SYSTEM_CASCADE));
    }

    /**
     * CLAUDE.md B.3 — periodic sweep for matched drivers who went dark instead of
     * heading to pickup. Runs the exact same re-dispatch cascade as an explicit
     * {@link #driverCancelBeforePickup} (the customer's request must survive), plus
     * records a strike via {@link DriverUnresponsiveEvent} so this driver sinks in
     * future matching priority — the system-side equivalent of "tự động chuyển sang
     * ứng viên kế tiếp, đồng thời hạ điểm ưu tiên hiển thị của tài xế đó".
     */
    @Scheduled(fixedDelayString = "PT1M")
    void sweepUnresponsiveDrivers() {
        var threshold = Instant.now().minus(DRIVER_RESPONSE_TIMEOUT);
        for (var trip : tripRepository.findAllByStatusAndCreatedAtBefore(TripStatus.STARTED, threshold)) {
            UUID bookingId = trip.getBookingId();
            UUID driverId = trip.getDriverId();
            UUID tripId = trip.getId();
            cancelAndPublish(trip, CancellationReason.DRIVER_UNRESPONSIVE);
            log.warn("Trip {} auto-cancelled — driver {} unresponsive past {} timeout; re-dispatching booking {} and recording a strike",
                    tripId, driverId, DRIVER_RESPONSE_TIMEOUT, bookingId);
            events.publishEvent(new DriverCancelledBeforePickupEvent(bookingId, tripId, driverId));
            events.publishEvent(new DriverUnresponsiveEvent(driverId, tripId));
        }
    }

    /**
     * CLAUDE.md C.7 — periodic sweep for trips where the driver's GPS trail has gone
     * cold mid-route (they're physically holding the customer's car — this is a safety
     * concern, not just a billing nuisance). Deliberately does NOT cancel or otherwise
     * touch the trip — losing a signal is not evidence of wrongdoing, and disrupting an
     * already-difficult situation would be worse than leaving it to support to reach out.
     *
     * Fires {@link GpsSignalLostEvent} exactly once per loss episode: a driver whose
     * last fix sits in the narrow "just crossed {@link #GPS_LOSS_THRESHOLD}" window
     * (one sweep cycle wide) is alerted on; the same stale fix will have aged out of
     * that window by the next cycle, so it never re-fires for as long as the signal
     * stays lost — exactly one heads-up, not a flood of repeats. Drivers who never
     * reported at all during this trip are covered too, anchored on {@code Trip#getCreatedAt}.
     */
    @Scheduled(fixedDelayString = "PT1M")
    void sweepStaleGpsTrips() {
        var now = Instant.now();
        var staleSince = now.minus(GPS_LOSS_THRESHOLD);
        var justCrossedAfter = staleSince.minus(GPS_SWEEP_INTERVAL);

        for (var trip : tripRepository.findAllByStatus(TripStatus.IN_PROGRESS)) {
            var lastKnownAt = driverLocationFreshnessPort.lastReportedAt(trip.getDriverId()).orElse(trip.getCreatedAt());
            if (lastKnownAt.isBefore(staleSince) && lastKnownAt.isAfter(justCrossedAfter)) {
                log.warn("Trip {} — driver {} GPS signal lost (last seen {}, threshold {}); alerting customer, driver and support",
                        trip.getId(), trip.getDriverId(), lastKnownAt, GPS_LOSS_THRESHOLD);
                events.publishEvent(new GpsSignalLostEvent(trip.getId(), trip.getCustomerId(), trip.getDriverId(), lastKnownAt));
            }
        }
    }

    /**
     * CLAUDE.md C.6 — fires immediately on raise, before anything else: support and
     * the other party need to know NOW, not after some review step. Open to either
     * participant (never the admin — admins observe the queue, they don't raise into it)
     * and blocked only once the trip is truly over, since there's nothing left to act on.
     */
    @Override
    public void raiseSos(UUID tripId, AuthenticatedAccount requester, String note) {
        var trip = findOrThrow(tripId);
        requireParticipant(trip, requester);
        if (requester.isAdmin()) {
            throw new ForbiddenException("Chỉ khách hàng hoặc tài xế của chuyến đi mới có thể báo khẩn cấp");
        }
        if (trip.isTerminal()) {
            throw new ForbiddenException("Không thể báo khẩn cấp cho chuyến đi đã kết thúc: " + trip.getId());
        }

        var now = Instant.now();
        var alert = sosAlertRepository.save(new SosAlert(trip.getId(), requester.profileId(), note, now));
        log.warn("SOS raised on trip {} by profile {} at {}: {}", trip.getId(), requester.profileId(), now, note);
        events.publishEvent(new SosRaisedEvent(alert.getId(), trip.getId(), requester.profileId(),
                trip.getCustomerId(), trip.getDriverId(), now, note));
    }

    @Override
    public List<SosAlertResponse> listSosAlerts(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ quản trị viên mới có thể xem danh sách báo khẩn cấp");
        }
        return sosAlertRepository.findAllByOrderByRaisedAtDesc().stream()
                .map(a -> new SosAlertResponse(a.getId(), a.getTripId(), a.getRaisedByProfileId(), a.getNote(), a.getRaisedAt()))
                .toList();
    }

    @Override
    public List<RouteDeviationFlagResponse> listRouteDeviationFlags(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ quản trị viên mới có thể xem danh sách cảnh báo lệch lộ trình");
        }
        return routeDeviationFlagRepository.findAllByOrderByFlaggedAtDesc().stream()
                .map(f -> new RouteDeviationFlagResponse(f.getId(), f.getTripId(), f.getDriverId(),
                        f.getExpectedDistanceKm(), f.getActualDistanceKm(), f.getDeviationRatio(), f.getFlaggedAt()))
                .toList();
    }

    private void cancelAndPublish(Trip trip, CancellationReason reason) {
        trip.cancel(reason);
        tripRepository.save(trip);
        events.publishEvent(new TripCancelledEvent(trip.getId(), trip.getBookingId(), trip.getDriverId()));
    }

    private void requireAssignedDriver(Trip trip, AuthenticatedAccount requester) {
        if (!requester.ownsProfile(trip.getDriverId())) {
            throw new ForbiddenException("Chỉ tài xế được phân công mới có thể thao tác trên chuyến đi này");
        }
    }

    private void requireParticipant(Trip trip, AuthenticatedAccount requester) {
        if (requester.isAdmin()) {
            return;
        }
        if (!requester.ownsProfile(trip.getCustomerId()) && !requester.ownsProfile(trip.getDriverId())) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên chuyến đi này");
        }
    }

    private Trip findOrThrow(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi: " + tripId));
    }

    private TripResponse toResponse(Trip trip) {
        return new TripResponse(trip.getId(), trip.getBookingId(), trip.getCustomerId(), trip.getDriverId(),
                trip.getStatus(), trip.getFinalFareAmount(), trip.getFinalFareCurrency(), trip.getCancellationReason());
    }
}
