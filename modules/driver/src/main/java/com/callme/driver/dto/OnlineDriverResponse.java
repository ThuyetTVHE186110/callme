package com.callme.driver.dto;

import java.util.UUID;

/**
 * The public projection of an online driver — just enough for a customer-facing
 * "drivers are around" view. Deliberately excludes the verification dossier
 * (background-check status, license/insurance expiry dates...) that
 * {@link DriverResponse} carries: that is compliance data for admins, not something
 * every logged-in customer should be able to harvest for the whole online fleet
 * (OWASP A01 — excessive data exposure).
 */
public record OnlineDriverResponse(UUID id, String displayName) {
}
