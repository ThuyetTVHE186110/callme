package com.callme.common.security;

/**
 * The role an authenticated account holds. Drives both Spring Security authorities
 * (as ROLE_xxx) and which profile (customer vs driver) `profileId` points to.
 */
public enum AccountRole {
    CUSTOMER,
    DRIVER,
    ADMIN
}
