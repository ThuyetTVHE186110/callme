package com.callme.trip.controller;

import com.callme.common.exception.NotFoundException;
import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.common.shared.GeoPoint;
import com.callme.trip.dto.AbortInProgressTripRequest;
import com.callme.trip.dto.ChangeDestinationRequest;
import com.callme.trip.dto.DestinationChangeResponse;
import com.callme.trip.dto.EmergencyAbortReportResponse;
import com.callme.trip.dto.IncidentReportResponse;
import com.callme.trip.dto.PickUpCustomerRequest;
import com.callme.trip.dto.RaiseIncidentRequest;
import com.callme.trip.dto.RaiseSosRequest;
import com.callme.trip.dto.ResolveIncidentRequest;
import com.callme.trip.dto.RouteDeviationFlagResponse;
import com.callme.trip.dto.SosAlertResponse;
import com.callme.trip.dto.TripResponse;
import com.callme.trip.dto.VehicleBreakdownRequest;
import com.callme.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripResponse> get(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.get(tripId, account));
    }

    /** Returns the most recent trip for a given booking — use this right after booking confirmation to get the tripId. */
    @GetMapping("/by-booking/{bookingId}")
    public ApiResponse<TripResponse> getByBooking(@PathVariable UUID bookingId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.getByBooking(bookingId, account));
    }

    /**
     * Driver app entry point: returns the caller's current active trip
     * (STARTED / ARRIVED_AT_PICKUP / IN_PROGRESS). Call this after receiving a
     * BOOKING_CONFIRMED notification to get the tripId needed for all subsequent
     * driver actions. Returns 404 when no active trip exists (idle state).
     */
    @GetMapping("/my-active")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TripResponse> getActiveForDriver(@AuthenticationPrincipal AuthenticatedAccount account) {
        return tripService.getActiveForDriver(account)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new NotFoundException("Không có chuyến đang hoạt động"));
    }

    /** CLAUDE.md §5 — driver reaches the pickup point (not yet behind the wheel); starts the no-show clock. */
    @PutMapping("/{tripId}/arrive")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> arriveAtPickup(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.arriveAtPickup(tripId, account);
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md §5 / C.2 — driver attests they checked the customer/vehicle and now
     * takes the wheel; trip becomes IN_PROGRESS. {@code identityVerified} must be
     * explicitly {@code true} ({@link PickUpCustomerRequest}) — there is no
     * convenience default, by design.
     */
    @PutMapping("/{tripId}/pick-up")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> pickUpCustomer(@PathVariable UUID tripId,
                                            @Valid @RequestBody PickUpCustomerRequest request,
                                            @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.pickUpCustomer(tripId, account, request.identityVerified());
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md C.1 — driver declares the customer never showed up after waiting out
     * the grace period at the pickup point. Gated server-side on both trip status and
     * elapsed wait ({@link com.callme.trip.entity.Trip#cancelNoShow}).
     */
    @PutMapping("/{tripId}/no-show")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> cancelNoShow(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.cancelNoShow(tripId, account);
        return ApiResponse.ok(null);
    }

    /** Only the assigned driver may complete — the service enforces assignment; the role gate here keeps the endpoint consistent with every other driver action. */
    @PutMapping("/{tripId}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> complete(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.complete(tripId, account);
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md C.3 — driver reports the customer's vehicle won't start / broke down
     * mid-route. Aborts the trip with a fee-neutral, support-routed reason — this is
     * the customer's risk (their car), not a fault dispute between participants.
     */
    @PutMapping("/{tripId}/vehicle-breakdown")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> reportVehicleBreakdown(@PathVariable UUID tripId,
                                                     @Valid @RequestBody VehicleBreakdownRequest request,
                                                     @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.reportVehicleBreakdown(tripId, account, request.note());
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md C.5 — customer redirects the trip mid-route. Only the riding
     * customer may do this, and only once the driver actually has the wheel
     * (IN_PROGRESS) — see {@link TripService#changeDestination}. Returns the
     * re-quoted fare so the new price is in the customer's hands immediately
     * (CLAUDE.md A.4); both parties also get an in-app notification.
     */
    @PutMapping("/{tripId}/destination")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<DestinationChangeResponse> changeDestination(@PathVariable UUID tripId,
                                                @Valid @RequestBody ChangeDestinationRequest request,
                                                @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.changeDestination(tripId, account, new GeoPoint(request.latitude(), request.longitude())));
    }

    /**
     * Driver backs out before reaching the customer (CLAUDE.md B.4) — distinct from
     * {@link #cancel}: the booking is re-dispatched to a replacement driver rather
     * than cancelled outright. Only legal while the trip is still STARTED.
     */
    @PutMapping("/{tripId}/driver-cancel-before-pickup")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> driverCancelBeforePickup(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.driverCancelBeforePickup(tripId, account);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{tripId}")
    public ApiResponse<Void> cancel(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.cancel(tripId, account);
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md E.2 — the only legitimate way for the assigned driver to end an
     * IN_PROGRESS trip early. The request body's coordinates double as the driver's
     * attestation that the customer's car and the customer are *already* parked
     * safely there — not a request for permission to do so. {@link #cancel} actively
     * refuses driver-initiated cancellation from this status; this is the door
     * CLAUDE.md insists must exist instead, complete with a CSKH worklist entry.
     */
    @PutMapping("/{tripId}/abort-in-progress")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> abortInProgressTrip(@PathVariable UUID tripId,
                                                  @Valid @RequestBody AbortInProgressTripRequest request,
                                                  @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.abortInProgressTrip(tripId, account, new GeoPoint(request.safeLatitude(), request.safeLongitude()), request.note());
        return ApiResponse.ok(null);
    }

    /** CSKH/admin worklist of mid-trip emergency aborts — CLAUDE.md E.2 ("tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS"). */
    @GetMapping("/emergency-aborts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<EmergencyAbortReportResponse>> listEmergencyAbortReports(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.listEmergencyAbortReports(account));
    }

    /**
     * CLAUDE.md C.6 — emergency button. Deliberately the lightest-weight endpoint in
     * this controller: no body validation beyond an optional note, available to either
     * participant the moment something goes wrong, mid-trip or not.
     */
    @PostMapping("/{tripId}/sos")
    public ApiResponse<Void> raiseSos(@PathVariable UUID tripId,
                                      @Valid @RequestBody RaiseSosRequest request,
                                      @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.raiseSos(tripId, account, request.note());
        return ApiResponse.ok(null);
    }

    /** CSKH/admin worklist of raised emergencies — CLAUDE.md C.6. */
    @GetMapping("/sos")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SosAlertResponse>> listSosAlerts(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.listSosAlerts(account));
    }

    /** CSKH/admin worklist of auto-raised route-deviation flags — CLAUDE.md C.8 ("tài xế cố tình đi đường vòng để tăng cước"). */
    @GetMapping("/route-deviations")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RouteDeviationFlagResponse>> listRouteDeviationFlags(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.listRouteDeviationFlags(account));
    }

    /**
     * CLAUDE.md §4.2 — either participant reports a collision/incident involving the
     * customer's vehicle. Queues the report for CSKH's liability investigation.
     */
    @PostMapping("/{tripId}/incidents")
    public ApiResponse<Void> reportIncident(@PathVariable UUID tripId,
                                             @Valid @RequestBody RaiseIncidentRequest request,
                                             @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.reportIncident(tripId, account, request.description());
        return ApiResponse.ok(null);
    }

    /** CSKH/admin worklist of reported incidents — CLAUDE.md §4.2. */
    @GetMapping("/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<IncidentReportResponse>> listIncidentReports(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(tripService.listIncidentReports(account));
    }

    /** CSKH records the liability finding for a reported incident — CLAUDE.md §4.2. */
    @PutMapping("/incidents/{incidentId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resolveIncident(@PathVariable UUID incidentId,
                                              @Valid @RequestBody ResolveIncidentRequest request,
                                              @AuthenticationPrincipal AuthenticatedAccount account) {
        tripService.resolveIncident(incidentId, account, request.status(), request.resolutionNote());
        return ApiResponse.ok(null);
    }
}
