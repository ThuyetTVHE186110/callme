package com.callme.payment.service;

import com.callme.common.security.AuthenticatedAccount;
import com.callme.payment.dto.PaymentResponse;
import com.callme.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    UUID openForTrip(UUID tripId, UUID customerId, UUID driverId, BigDecimal amount, String currency);

    /** Either party to the trip — the paying customer or the collecting driver — may view it; admins always may. */
    PaymentResponse getByTrip(UUID tripId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md D.1/§4.3 — who may attest a settlement depends on the method: CASH is
     * confirmed by the **driver** (the party physically receiving the money — a
     * customer self-declaring "đã đưa tiền mặt" would leave every "đưa rồi/chưa đưa"
     * dispute with no counterparty attestation), IN_APP by the **customer** (it's
     * their wallet/card). Admins may confirm either, for CSKH-mediated settlements.
     */
    void confirm(UUID paymentId, PaymentMethod method, AuthenticatedAccount requester);

    /**
     * CLAUDE.md D.1 — either participant (or admin) may report the settlement failed.
     * In the flagship D.1 scenario it is the **driver** standing at the kerb with a
     * customer who refuses/can't pay cash; for D.3 it is the customer whose in-app
     * charge was declined. Both roads lead to the same FAILED state and, after the
     * retries run out, the same CSKH worklist.
     */
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
