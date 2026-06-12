package com.callme.driver.entity;

/**
 * CLAUDE.md §4.5 — "lý lịch tư pháp hợp lệ (không tiền án/tiền sự liên quan đến an toàn,
 * tài sản)". A new driver registration starts at {@link #PENDING} and is not eligible to
 * go online (CLAUDE.md §4.5 invariant) until an admin records the result of the check via
 * {@code DriverService#recordVerification}.
 */
public enum BackgroundCheckStatus {
    PENDING,
    APPROVED,
    REJECTED
}
