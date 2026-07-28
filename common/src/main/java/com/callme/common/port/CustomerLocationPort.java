package com.callme.common.port;

import com.callme.common.port.dto.LocationSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Published by the identity module (which owns {@code Customer}); consumed by trip
 * so the assigned driver can see where the customer currently is, without depending
 * on identity's entity internals.
 */
public interface CustomerLocationPort {

    /** This customer's last known GPS fix (lat/lng + when), or empty if they have never reported one. */
    Optional<LocationSnapshot> currentLocation(UUID customerId);
}
