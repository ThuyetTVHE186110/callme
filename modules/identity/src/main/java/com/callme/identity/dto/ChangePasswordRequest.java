package com.callme.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CLAUDE.md §4.8.2 — self-service password change: requires the current password, revokes all live tokens. */
public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống") String currentPassword,
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu mới phải có ít nhất 8 ký tự") String newPassword) {
}
