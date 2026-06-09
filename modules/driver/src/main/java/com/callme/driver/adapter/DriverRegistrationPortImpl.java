package com.callme.driver.adapter;

import com.callme.common.port.DriverRegistrationPort;
import com.callme.driver.service.DriverService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter side of the DriverRegistrationPort contract — lets identity's AuthService
 * create a Driver profile when a new account registers with role DRIVER, without
 * identity holding a compile-time dependency on this module.
 */
@Component
public class DriverRegistrationPortImpl implements DriverRegistrationPort {

    private final DriverService driverService;

    public DriverRegistrationPortImpl(DriverService driverService) {
        this.driverService = driverService;
    }

    @Override
    public UUID registerDriver(String displayName) {
        return driverService.register(displayName);
    }
}
