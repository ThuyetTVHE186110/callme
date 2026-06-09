package com.callme.payment.controller;

import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.payment.dto.ConfirmPaymentRequest;
import com.callme.payment.dto.PaymentResponse;
import com.callme.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/by-trip/{tripId}")
    public ApiResponse<PaymentResponse> getByTrip(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(paymentService.getByTrip(tripId, account));
    }

    @PutMapping("/{paymentId}/confirm")
    public ApiResponse<Void> confirm(@PathVariable UUID paymentId, @Valid @RequestBody ConfirmPaymentRequest request,
                                     @AuthenticationPrincipal AuthenticatedAccount account) {
        paymentService.confirm(paymentId, request.method(), account);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{paymentId}/fail")
    public ApiResponse<Void> markFailed(@PathVariable UUID paymentId, @AuthenticationPrincipal AuthenticatedAccount account) {
        paymentService.markFailed(paymentId, account);
        return ApiResponse.ok(null);
    }

    /** CLAUDE.md D.3 — customer re-attempts a failed in-app charge; capped server-side in {@link com.callme.payment.entity.Payment#retry()}. */
    @PutMapping("/{paymentId}/retry")
    public ApiResponse<Void> retry(@PathVariable UUID paymentId, @AuthenticationPrincipal AuthenticatedAccount account) {
        paymentService.retry(paymentId, account);
        return ApiResponse.ok(null);
    }

    /** Refunds are an admin/back-office action (CLAUDE.md D.4) — gated in the service layer. */
    @PostMapping("/{paymentId}/refund")
    public ApiResponse<Void> refund(@PathVariable UUID paymentId, @AuthenticationPrincipal AuthenticatedAccount account) {
        paymentService.refund(paymentId, account);
        return ApiResponse.ok(null);
    }

    /** CSKH/admin worklist of payments stuck FAILED past every retry — CLAUDE.md D.1 ("khách từ chối thanh toán hoặc không đủ tiền mặt"). */
    @GetMapping("/unsettled")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentResponse>> listUnsettled(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(paymentService.listUnsettled(account));
    }
}
