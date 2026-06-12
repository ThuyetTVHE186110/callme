package com.callme.driver.service;

import com.callme.driver.dto.DriverResponse;
import com.callme.driver.dto.OnlineDriverResponse;
import com.callme.driver.entity.BackgroundCheckStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DriverService {

    /** Public projection only — the verification dossier stays admin-only via {@link #getDriver} (OWASP A01). */
    List<OnlineDriverResponse> listOnlineDrivers();

    /** Full driver record including the §4.5 verification dossier — for the admin verification workflow. */
    DriverResponse getDriver(UUID driverId);

    UUID register(String displayName);

    /**
     * CLAUDE.md §4.5/§4.2 invariant — going online ({@code online = true}) is rejected
     * (409) if the driver isn't currently eligible (background check, license, insurance,
     * reverification — see {@link com.callme.driver.entity.Driver#goOnline}). Going
     * offline always succeeds.
     */
    void setOnline(UUID driverId, boolean online);

    void updateLocation(UUID driverId, double latitude, double longitude);

    /** Releases the driver back into the matching pool once their trip ends (completed or cancelled). */
    void markAvailable(UUID driverId);

    /** CLAUDE.md B.3 — records that this driver was matched but went unresponsive (didn't head to pickup); lowers their future matching priority. */
    void recordNoResponseStrike(UUID driverId);

    /** CLAUDE.md §4.5/§4.2 — admin records the outcome of a periodic reverification round. */
    void recordVerification(UUID driverId, BackgroundCheckStatus backgroundCheckStatus, LocalDate licenseExpiryDate, LocalDate insuranceValidUntil);
}
