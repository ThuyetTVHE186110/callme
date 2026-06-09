package com.callme.identity.dto;

import com.callme.common.security.AccountRole;

import java.util.UUID;

public record AuthResponse(String token, UUID accountId, UUID profileId, AccountRole role) {
}
