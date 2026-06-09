package com.callme.payment.repository;

import com.callme.payment.entity.Payment;
import com.callme.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTripId(UUID tripId);

    /**
     * CLAUDE.md D.1 — CSKH worklist of payments stuck in {@code FAILED} that have
     * exhausted every retry ({@link Payment#MAX_RETRIES}): exactly the "khách từ chối
     * thanh toán hoặc không đủ tiền mặt" cases that {@link Payment#retry} itself
     * already refuses to keep handling and points toward "liên hệ tổng đài".
     */
    List<Payment> findByStatusAndRetryCountGreaterThanEqualOrderByIdDesc(PaymentStatus status, int retryCount);
}
