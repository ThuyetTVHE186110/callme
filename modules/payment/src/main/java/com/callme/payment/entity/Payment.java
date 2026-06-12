package com.callme.payment.entity;

import com.callme.common.exception.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(force = true)
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;

    private UUID customerId;

    /**
     * CLAUDE.md D.1 — the driver is the party physically collecting cash at the kerb,
     * so they must be able to see this payment and report "khách từ chối thanh toán /
     * không đủ tiền mặt". Without their id on the record, the D.1 CSKH queue could
     * only ever be fed by the (often intoxicated, uncooperative) customer voluntarily
     * reporting their own refusal — i.e. never.
     */
    private UUID driverId;

    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    /**
     * CLAUDE.md D.3 — bounds how many times a customer may retry a failed in-app
     * charge before this stops being "the gateway timed out" and starts being
     * "công nợ exposure that needs a human (CSKH/đối soát) to step in".
     */
    private int retryCount;

    /**
     * CLAUDE.md D.1 — once a {@code FAILED} payment has exhausted every retry, it stops
     * being a transient settlement hiccup and becomes exactly the kind of "khách từ
     * chối/không đủ tiền mặt" exposure that needs CSKH to step in (giữ giấy tờ tạm
     * thời? thanh toán trễ qua app? báo cáo thu hồi công nợ?). Public so
     * {@code PaymentRepository}'s CSKH-worklist query can express that threshold
     * without duplicating the magic number.
     */
    public static final int MAX_RETRIES = 3;

    /** Opened the moment a trip completes — defaults to cash-on-arrival until the customer settles (CLAUDE.md flow step 10). */
    public Payment(UUID tripId, UUID customerId, UUID driverId, BigDecimal amount, String currency) {
        this.tripId = tripId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.amount = amount;
        this.currency = currency;
        this.method = PaymentMethod.CASH;
        this.status = PaymentStatus.PENDING;
    }

    public void confirm(PaymentMethod method) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm payment in status " + this.status + ": " + this.id);
        }
        this.method = method;
        this.status = PaymentStatus.COMPLETED;
    }

    /** CLAUDE.md edge case D.1: customer (often still intoxicated) cannot or refuses to settle on the spot. */
    public void markFailed() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot fail payment in status " + this.status + ": " + this.id);
        }
        this.status = PaymentStatus.FAILED;
    }

    /**
     * CLAUDE.md edge case D.3 — gives a customer whose card was declined or whose
     * gateway timed out a way back to PENDING without re-opening a fresh payment
     * record (which would fragment the audit trail / đối soát công nợ). Capped at
     * {@link #MAX_RETRIES}: beyond that, repeated failures stop looking like transient
     * gateway flakiness and need CSKH to step in (e.g. switch the customer to cash,
     * escalate collection) rather than the customer hammering "thử lại" indefinitely.
     */
    public void retry() {
        if (this.status != PaymentStatus.FAILED) {
            throw new IllegalStateException("Cannot retry payment in status " + this.status + ": " + this.id);
        }
        if (this.retryCount >= MAX_RETRIES) {
            throw new ConflictException("Đã vượt quá số lần thử lại thanh toán (" + MAX_RETRIES + ") — vui lòng liên hệ tổng đài để được hỗ trợ");
        }
        this.retryCount++;
        this.status = PaymentStatus.PENDING;
    }

    /** CLAUDE.md edge case D.4: post-completion refund following a fare dispute / complaint resolution. */
    public void refund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot refund payment in status " + this.status + ": " + this.id);
        }
        this.status = PaymentStatus.REFUNDED;
    }
}
