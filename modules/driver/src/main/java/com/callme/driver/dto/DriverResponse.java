package com.callme.driver.dto;

import java.util.UUID;

public record DriverResponse(UUID id, String displayName, boolean online) {
}
