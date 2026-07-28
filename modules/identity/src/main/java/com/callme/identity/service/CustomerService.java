package com.callme.identity.service;

import com.callme.identity.dto.CustomerResponse;
import com.callme.identity.dto.RegisterCustomerRequest;

import java.util.UUID;

public interface CustomerService {

    UUID register(RegisterCustomerRequest request);

    CustomerResponse get(UUID customerId);

    /** Called by {@code CustomerLocationEventListener} whenever the location module reports a fresh customer GPS fix. */
    void updateLocation(UUID customerId, double latitude, double longitude);
}
