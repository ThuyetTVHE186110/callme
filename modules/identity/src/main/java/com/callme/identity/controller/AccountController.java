package com.callme.identity.controller;

import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.identity.dto.ChangePasswordRequest;
import com.callme.identity.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * CLAUDE.md C.4/C.6/G.5 — CSKH suspends an account (e.g. a driver after a safety
     * incident). Effective on the target's next request: the JWT filter re-checks
     * {@code Account.active} per request, so their still-valid 24h token stops working
     * immediately. This endpoint is what makes that mechanism operable — without it,
     * suspension only existed as a hand-run UPDATE statement.
     */
    @PutMapping("/{accountId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable UUID accountId, @AuthenticationPrincipal AuthenticatedAccount account) {
        accountService.deactivate(accountId, account);
        return ApiResponse.ok(null);
    }

    /** Lifts a suspension once CSKH resolves whatever triggered it. */
    @PutMapping("/{accountId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> reactivate(@PathVariable UUID accountId, @AuthenticationPrincipal AuthenticatedAccount account) {
        accountService.reactivate(accountId, account);
        return ApiResponse.ok(null);
    }

    /**
     * CLAUDE.md §4.8.2 — self-service password change. Lives here (authenticated
     * surface) and NOT under /api/auth/** (permitAll). Side effect by design: the
     * tokenVersion bump revokes every live token including the one making this call
     * — the client must log in again with the new password.
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            @AuthenticationPrincipal AuthenticatedAccount account) {
        accountService.changePassword(account, request.currentPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }
}
