package com.callme.identity.service;

import com.callme.identity.dto.CustomerResponse;
import com.callme.identity.dto.RegisterCustomerRequest;

import java.util.UUID;

public interface CustomerService {

    UUID register(RegisterCustomerRequest request);

    CustomerResponse get(UUID customerId);
}
