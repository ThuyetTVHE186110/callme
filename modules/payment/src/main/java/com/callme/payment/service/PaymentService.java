package com.callme.payment.service;

import com.callme.common.security.AuthenticatedAccount;
import com.callme.payment.dto.PaymentResponse;
import com.callme.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    UUID openForTrip(UUID tripId, UUID customerId, BigDecimal amount, String currency);

    PaymentResponse getByTrip(UUID tripId, AuthenticatedAccount requester);

    void confirm(UUID paymentId, PaymentMethod method, AuthenticatedAccount requester);

    void markFailed(UUID paymentId, AuthenticatedAccount requester);

    /** CLAUDE.md D.3 — customer re-attempts a failed in-app charge (FAILED -> PENDING), capped server-side. */
    void retry(UUID paymentId, AuthenticatedAccount requester);

    void refund(UUID paymentId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md D.1 — admin/CSKH worklist of payments that have refused to settle
     * after every retry: "khách từ chối thanh toán hoặc không đủ tiền mặt" cases that
     * need a human to step in (báo cáo CSKH — đối soát công nợ / thanh toán trễ).
     */
    List<PaymentResponse> listUnsettled(AuthenticatedAccount requester);
}
