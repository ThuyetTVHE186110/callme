package com.callme.payment.service.impl;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.payment.dto.PaymentResponse;
import com.callme.payment.entity.Payment;
import com.callme.payment.entity.PaymentMethod;
import com.callme.payment.entity.PaymentStatus;
import com.callme.payment.repository.PaymentRepository;
import com.callme.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public UUID openForTrip(UUID tripId, UUID customerId, BigDecimal amount, String currency) {
        return paymentRepository.save(new Payment(tripId, customerId, amount, currency)).getId();
    }

    @Override
    public PaymentResponse getByTrip(UUID tripId, AuthenticatedAccount requester) {
        var payment = paymentRepository.findByTripId(tripId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thanh toán cho chuyến đi: " + tripId));
        requireOwner(payment, requester);
        return toResponse(payment);
    }

    @Override
    public void confirm(UUID paymentId, PaymentMethod method, AuthenticatedAccount requester) {
        var payment = findOrThrow(paymentId);
        requireOwner(payment, requester);
        payment.confirm(method);
        paymentRepository.save(payment);
    }

    @Override
    public void markFailed(UUID paymentId, AuthenticatedAccount requester) {
        var payment = findOrThrow(paymentId);
        requireOwner(payment, requester);
        payment.markFailed();
        paymentRepository.save(payment);
    }

    /** CLAUDE.md D.3 — only the paying customer retries their own charge; admins resolve via refund/escalation, not by replaying payment attempts on someone's behalf. */
    @Override
    public void retry(UUID paymentId, AuthenticatedAccount requester) {
        var payment = findOrThrow(paymentId);
        if (!requester.ownsProfile(payment.getCustomerId())) {
            throw new ForbiddenException("Bạn không có quyền thử lại thanh toán này");
        }
        payment.retry();
        paymentRepository.save(payment);
    }

    /** Refunds reverse a settled payment after a fare dispute (CLAUDE.md D.4) — a back-office decision, not something either party self-serves. */
    @Override
    public void refund(UUID paymentId, AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ quản trị viên mới có thể hoàn tiền");
        }
        var payment = findOrThrow(paymentId);
        payment.refund();
        paymentRepository.save(payment);
    }

    /** CLAUDE.md D.1 — admin-only; this is precisely the queue CSKH works from when a customer "từ chối thanh toán hoặc không đủ tiền mặt" past every retry. */
    @Override
    public List<PaymentResponse> listUnsettled(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ CSKH/quản trị viên mới có thể xem hàng đợi công nợ");
        }
        return paymentRepository.findByStatusAndRetryCountGreaterThanEqualOrderByIdDesc(PaymentStatus.FAILED, Payment.MAX_RETRIES).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireOwner(Payment payment, AuthenticatedAccount requester) {
        if (!requester.isAdmin() && !requester.ownsProfile(payment.getCustomerId())) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên thanh toán này");
        }
    }

    private Payment findOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thanh toán: " + paymentId));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getTripId(), payment.getCustomerId(),
                payment.getAmount(), payment.getCurrency(), payment.getMethod(), payment.getStatus());
    }
}
