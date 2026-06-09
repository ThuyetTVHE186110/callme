package com.callme.location.controller;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.location.dto.ReportLocationRequest;
import com.callme.location.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /** Only the driver themselves can report their own GPS position — never on behalf of someone else. */
    @PostMapping("/{driverId}")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> report(@PathVariable UUID driverId, @Valid @RequestBody ReportLocationRequest request,
                                    @AuthenticationPrincipal AuthenticatedAccount account) {
        if (!account.ownsProfile(driverId)) {
            throw new ForbiddenException("Bạn chỉ có thể báo cáo vị trí của chính mình");
        }
        locationService.reportLocation(driverId, request.latitude(), request.longitude());
        return ApiResponse.ok(null);
    }
}
