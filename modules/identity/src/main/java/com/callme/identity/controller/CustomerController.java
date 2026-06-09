package com.callme.identity.controller;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.identity.dto.CustomerResponse;
import com.callme.identity.service.CustomerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID customerId, @AuthenticationPrincipal AuthenticatedAccount account) {
        if (!account.isAdmin() && !account.ownsProfile(customerId)) {
            throw new ForbiddenException("Bạn không có quyền xem hồ sơ khách hàng này");
        }
        return ApiResponse.ok(customerService.get(customerId));
    }
}
