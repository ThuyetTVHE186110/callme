package com.callme.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Re-send a verification OTP / start a forgot-password flow (CLAUDE.md §4.8.1–2) — phone only, response deliberately uniform. */
public record PhoneNumberRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^[0-9+][0-9 ]{6,14}$", message = "Số điện thoại không hợp lệ") String phoneNumber) {
}
