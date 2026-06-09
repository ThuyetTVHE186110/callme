package com.callme.booking.controller;

import com.callme.booking.dto.BookingResponse;
import com.callme.booking.dto.CreateBookingRequest;
import com.callme.booking.service.BookingService;
import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Only customers can request a designated driver — and always for themselves, never
     * for someone else. The optional Idempotency-Key header lets flaky-network retries
     * (CLAUDE.md A.3) safely replay the original booking instead of creating a duplicate.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<UUID> create(@Valid @RequestBody CreateBookingRequest request,
                                    @AuthenticationPrincipal AuthenticatedAccount account,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(bookingService.requestDriverHome(account.profileId(), request, idempotencyKey));
    }

    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> get(@PathVariable UUID bookingId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(bookingService.get(bookingId, account));
    }

    @DeleteMapping("/{bookingId}")
    public ApiResponse<Void> cancel(@PathVariable UUID bookingId, @AuthenticationPrincipal AuthenticatedAccount account) {
        bookingService.cancel(bookingId, account);
        return ApiResponse.ok(null);
    }
}
