package com.callme.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** CLAUDE.md §4.8.1 — completes registration: proves the phone via OTP and receives the first token. */
public record VerifyPhoneRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^[0-9+][0-9 ]{6,14}$", message = "Số điện thoại không hợp lệ") String phoneNumber,
        @NotBlank(message = "Mã OTP không được để trống")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số") String code) {
}
