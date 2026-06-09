package com.callme.driver.service;

import com.callme.driver.dto.DriverResponse;

import java.util.List;
import java.util.UUID;

public interface DriverService {

    List<DriverResponse> listOnlineDrivers();

    UUID register(String displayName);

    void setOnline(UUID driverId, boolean online);

    void updateLocation(UUID driverId, double latitude, double longitude);

    /** Releases the driver back into the matching pool once their trip ends (completed or cancelled). */
    void markAvailable(UUID driverId);

    /** CLAUDE.md B.3 — records that this driver was matched but went unresponsive (didn't head to pickup); lowers their future matching priority. */
    void recordNoResponseStrike(UUID driverId);
}
