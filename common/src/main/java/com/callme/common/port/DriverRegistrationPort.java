package com.callme.common.port;

import java.util.UUID;

/**
 * Published by the driver module; consumed by identity's AuthService when a new
 * account registers with role DRIVER, so a Driver profile is created in the same
 * logical operation without identity holding a compile-time dependency on driver.
 *
 * The returned id becomes both the Driver.id and the owning Account.profileId,
 * keeping the two records addressable by the same identifier across modules.
 */
public interface DriverRegistrationPort {

    UUID registerDriver(String displayName);
}
