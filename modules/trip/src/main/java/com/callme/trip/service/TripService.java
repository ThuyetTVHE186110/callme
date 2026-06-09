package com.callme.trip.service;

import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.GeoPoint;
import com.callme.trip.dto.EmergencyAbortReportResponse;
import com.callme.trip.dto.RouteDeviationFlagResponse;
import com.callme.trip.dto.SosAlertResponse;
import com.callme.trip.dto.TripResponse;

import java.util.List;
import java.util.UUID;

public interface TripService {

    UUID startTrip(UUID bookingId, UUID customerId, UUID driverId, GeoPoint pickup, GeoPoint destination);

    TripResponse get(UUID tripId, AuthenticatedAccount requester);

    /** Look up the most recent trip belonging to a booking — allows the client to get tripId right after booking confirmation. */
    TripResponse getByBooking(UUID bookingId, AuthenticatedAccount requester);

    /** CLAUDE.md §5 — driver reaches the pickup point but has not yet taken the wheel; starts the no-show clock. */
    void arriveAtPickup(UUID tripId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md §5 / C.2 — driver attests they checked the customer is the
     * registered owner (and the vehicle matches the booking) and now takes the
     * wheel; the trip becomes IN_PROGRESS. {@code identityVerified=false} is
     * refused outright — see {@link com.callme.trip.entity.Trip#pickUpCustomer}.
     */
    void pickUpCustomer(UUID tripId, AuthenticatedAccount requester, boolean identityVerified);

    /**
     * CLAUDE.md C.1 — the customer never appeared at the pickup point after the
     * driver waited out the grace period. Only the assigned driver — physically
     * present and the only one who can know whether the customer truly never showed —
     * may declare it; the trip is cancelled with {@code CUSTOMER_NO_SHOW} so a later
     * fee policy can charge (or not) without conflating this with an ordinary cancel.
     */
    void cancelNoShow(UUID tripId, AuthenticatedAccount requester);

    void complete(UUID tripId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md C.3 — the customer's vehicle won't start or breaks down mid-route.
     * Squarely the customer's risk, not the driver's fault — the trip is aborted with
     * a dedicated {@link com.callme.common.shared.CancellationReason#VEHICLE_BREAKDOWN}
     * (no fee to either side) and handed to support for the "gọi cứu hộ / đổi sang
     * taxi thường" remediation. Only the assigned driver — physically with the vehicle
     * — can attest to this, and only once they've actually reached it (ARRIVED_AT_PICKUP
     * or IN_PROGRESS); reporting it from STARTED would mean reporting on a car they
     * haven't even seen yet.
     */
    void reportVehicleBreakdown(UUID tripId, AuthenticatedAccount requester, String note);

    /**
     * CLAUDE.md C.5 — customer changes the destination mid-trip. Re-quotes the fare
     * from the new route immediately (so both sides see the updated estimate before
     * arrival, not as a surprise at {@link #complete}) and logs old → new for later
     * dispute resolution. Only legal once the trip is IN_PROGRESS — the driver is
     * already behind the wheel, so this is a genuine route change, not an edit to
     * the original request.
     */
    void changeDestination(UUID tripId, AuthenticatedAccount requester, GeoPoint newDestination);

    /**
     * CLAUDE.md E.2 — the assigned driver may NOT use this to back out of an
     * IN_PROGRESS trip (they're physically holding the customer's car with the
     * customer aboard — "bỏ chuyến" mid-route is the domain's most dangerous
     * scenario); {@link #abortInProgressTrip} is the only legitimate exit from that
     * state. CSKH/admin retain the ability to cancel from any non-terminal status —
     * recorded as {@code FORCE_MAJEURE} — for cases reported and judged from their side.
     */
    void cancel(UUID tripId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md B.4 — the assigned driver backs out before reaching the customer
     * (trip still STARTED/ARRIVED_AT_PICKUP, i.e. they never took the wheel). This is
     * fundamentally different from a normal cancel: the customer's request must live
     * on and get re-dispatched to a replacement driver, not be cancelled outright.
     * Only legal pre-wheel — once the driver has the customer's car (IN_PROGRESS),
     * backing out becomes the much more serious "stranded mid-trip" situation
     * (CLAUDE.md E.2), which {@link #abortInProgressTrip} covers instead.
     */
    void driverCancelBeforePickup(UUID tripId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md E.2 — "tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS... tài
     * xế phải đưa xe + khách đến nơi an toàn trước khi được phép kết thúc bất thường".
     * The only legitimate way for the assigned driver to end an IN_PROGRESS trip
     * early: {@code safeLocation} is not just a record but an attestation — by
     * supplying it, the driver is declaring the customer's car and the customer
     * are already parked safely there. The trip is cancelled with the dedicated
     * {@link com.callme.common.shared.CancellationReason#DRIVER_EMERGENCY_ABORT}
     * reason (neither a casual-bail penalty nor an ordinary fee applies — this is
     * a safety incident, not a change of mind) and queued for CSKH, who judge
     * case-by-case whether a replacement driver needs to be sent to the spot.
     */
    void abortInProgressTrip(UUID tripId, AuthenticatedAccount requester, GeoPoint safeLocation, String note);

    /** CSKH/admin queue of mid-trip emergency aborts, most recent first — CLAUDE.md E.2 (mirrors {@link #listSosAlerts}). */
    List<EmergencyAbortReportResponse> listEmergencyAbortReports(AuthenticatedAccount requester);

    /**
     * System-triggered counterpart to {@link #cancel} — reacts to the owning booking
     * being cancelled. Idempotent and silent when no trip exists yet or it has already
     * reached a terminal state, since the booking and trip lifecycles run independently
     * and may race (e.g. the trip already completed moments before the cancel arrived).
     */
    void cancelForBooking(UUID bookingId);

    /**
     * CLAUDE.md C.6 — either trip participant raises an emergency. Deliberately not
     * gated by trip status: a customer being harassed or a driver in danger doesn't
     * stop being an emergency because the trip technically just turned IN_PROGRESS
     * or is about to complete — only a genuinely finished (terminal) trip is rejected,
     * since at that point there's no active situation left for support to intervene in.
     */
    void raiseSos(UUID tripId, AuthenticatedAccount requester, String note);

    /** CSKH/admin queue of raised emergencies, most recent first — CLAUDE.md C.6 / F.1 (escalation needs a worklist). */
    List<SosAlertResponse> listSosAlerts(AuthenticatedAccount requester);

    /** CSKH/admin queue of auto-raised route-deviation flags, most recent first — CLAUDE.md C.8. */
    List<RouteDeviationFlagResponse> listRouteDeviationFlags(AuthenticatedAccount requester);
}
