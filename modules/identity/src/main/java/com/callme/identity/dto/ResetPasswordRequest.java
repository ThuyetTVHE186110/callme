package com.callme.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** CLAUDE.md §4.8.2 — forgot-password: OTP (sent to the verified phone) + the new password, in one step. */
public record ResetPasswordRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^[0-9+][0-9 ]{6,14}$", message = "Số điện thoại không hợp lệ") String phoneNumber,
        @NotBlank(message = "Mã OTP không được để trống")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số") String code,
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu phải có ít nhất 8 ký tự") String newPassword) {
}
