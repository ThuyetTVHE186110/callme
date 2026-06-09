package com.callme.common.security;

import java.util.UUID;

/**
 * The authenticated principal extracted from a validated JWT and bound into the
 * Spring Security context by the infrastructure module's JwtAuthenticationFilter.
 * Controllers receive this via {@code @AuthenticationPrincipal} instead of trusting
 * customerId/driverId values supplied in request bodies.
 *
 * `profileId` is the id of the domain profile the account owns — a Customer.id when
 * role is CUSTOMER, or a Driver.id when role is DRIVER. For ADMIN accounts it has no
 * meaning and is null.
 */
public record AuthenticatedAccount(UUID accountId, UUID profileId, AccountRole role) {

    public boolean isCustomer() {
        return role == AccountRole.CUSTOMER;
    }

    public boolean isDriver() {
        return role == AccountRole.DRIVER;
    }

    public boolean isAdmin() {
        return role == AccountRole.ADMIN;
    }

    public boolean ownsProfile(UUID otherProfileId) {
        return profileId != null && profileId.equals(otherProfileId);
    }
}
