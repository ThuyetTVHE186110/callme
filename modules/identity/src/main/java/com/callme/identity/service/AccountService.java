package com.callme.identity.service;

import com.callme.common.security.AuthenticatedAccount;

import java.util.UUID;

/**
 * Admin-only account suspension lifecycle. This is the trigger for the per-request
 * {@code AccountStatusPort} check in the JWT filter: deactivating here takes effect
 * on the target's very next request, regardless of how long their token still lives
 * — the operational half of "đình chỉ tài xế giữa ca" (CLAUDE.md C.4/C.6/G.5) that
 * was previously only reachable by editing the database directly.
 */
public interface AccountService {

    void deactivate(UUID accountId, AuthenticatedAccount requester);

    void reactivate(UUID accountId, AuthenticatedAccount requester);

    /**
     * CLAUDE.md §4.8.2 — self-service password change: requires the current password
     * (a hijacked session must not be able to lock the real owner out by silently
     * rotating the credential) and revokes every live token via the tokenVersion bump
     * (§4.8.3) — including the session making this very call; the client re-logs in.
     */
    void changePassword(AuthenticatedAccount requester, String currentPassword, String newPassword);
}
