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
        @Size(min = 6, max = 100, message = "Mật khẩu phải có ít nhất 6 ký tự") String password,
        @Email(message = "Email không hợp lệ") String email,
        @NotNull(message = "Vai trò không được để trống") AccountRole role) {
}
