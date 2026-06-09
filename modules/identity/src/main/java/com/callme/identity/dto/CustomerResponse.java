package com.callme.identity.dto;

import java.util.UUID;

public record CustomerResponse(UUID id, String fullName, String phoneNumber, String email) {
}
