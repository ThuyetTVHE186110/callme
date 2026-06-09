package com.callme.driver.controller;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.driver.dto.DriverResponse;
import com.callme.driver.dto.SetOnlineRequest;
import com.callme.driver.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/online")
    public ApiResponse<List<DriverResponse>> listOnline() {
        return ApiResponse.ok(driverService.listOnlineDrivers());
    }

    /**
     * Drivers can only flip their own online status — registration binds Driver.id
     * to the authenticated account's profileId (see DriverRegistrationPort), so a
     * path-variable mismatch means someone is trying to act as another driver.
     */
    @PutMapping("/{driverId}/online-status")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Void> setOnline(@PathVariable UUID driverId, @Valid @RequestBody SetOnlineRequest request,
                                       @AuthenticationPrincipal AuthenticatedAccount account) {
        requireSelf(driverId, account);
        driverService.setOnline(driverId, request.online());
        return ApiResponse.ok(null);
    }

    private void requireSelf(UUID driverId, AuthenticatedAccount account) {
        if (!account.ownsProfile(driverId)) {
            throw new ForbiddenException("Bạn chỉ có thể thao tác trên hồ sơ tài xế của chính mình");
        }
    }
}
