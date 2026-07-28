package com.callme.identity.adapter;

import com.callme.common.port.CustomerLocationPort;
import com.callme.common.port.dto.LocationSnapshot;
import com.callme.identity.entity.Customer;
import com.callme.identity.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Implements the cross-module contract published in `common` — backs the trip module's customer-location read endpoint. */
@Service
public class CustomerLocationPortImpl implements CustomerLocationPort {

    private final CustomerRepository customerRepository;

    public CustomerLocationPortImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public Optional<LocationSnapshot> currentLocation(UUID customerId) {
        return customerRepository.findById(customerId)
                .filter(customer -> customer.getLastLocationUpdatedAt() != null)
                .map(customer -> new LocationSnapshot(customer.getLastKnownLatitude(), customer.getLastKnownLongitude(), customer.getLastLocationUpdatedAt()));
    }
}
