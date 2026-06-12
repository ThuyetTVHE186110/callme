package com.callme.identity.dto;

import com.callme.common.security.AccountRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration request. `role` is restricted to CUSTOMER or DRIVER —
 * ADMIN accounts are provisioned out-of-band, never through this open endpoint.
 */
public record RegisterRequest(
        @NotBlank(message = "Họ tên không được để trống") String fullName,
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^[0-9+][0-9 ]{6,14}$", message = "Số điện thoại không hợp lệ") String phoneNumber,
        @NotBlank(message = "Mật khẩu không được để trống")
        // OWASP A07 — 8 is the floor for accounts that gate someone's real-time
        // location and physical vehicle; complexity rules and a breached-password
        // check belong to the credential-lifecycle work (CLAUDE.md §4.8).
        @Size(min = 8, max = 100, message = "Mật khẩu phải có ít nhất 8 ký tự") String password,
        @Email(message = "Email không hợp lệ") String email,
        @NotNull(message = "Vai trò không được để trống") AccountRole role) {
}
