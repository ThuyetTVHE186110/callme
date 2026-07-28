package com.callme.location.service;

import java.util.UUID;

public interface LocationService {

    void reportLocation(UUID driverId, double latitude, double longitude);

    void reportCustomerLocation(UUID customerId, double latitude, double longitude);
}
