package com.callme.identity.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.NotFoundException;
import com.callme.identity.dto.CustomerResponse;
import com.callme.identity.dto.RegisterCustomerRequest;
import com.callme.identity.entity.Customer;
import com.callme.identity.repository.CustomerRepository;
import com.callme.identity.service.CustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UUID register(RegisterCustomerRequest request) {
        customerRepository.findByPhoneNumber(request.phoneNumber()).ifPresent(existing -> {
            throw new ConflictException("Số điện thoại đã được đăng ký: " + request.phoneNumber());
        });
        var customer = new Customer(request.fullName(), request.phoneNumber(), request.email());
        return customerRepository.save(customer).getId();
    }

    @Override
    public CustomerResponse get(UUID customerId) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng: " + customerId));
        return new CustomerResponse(customer.getId(), customer.getFullName(), customer.getPhoneNumber(), customer.getEmail());
    }

    @Override
    public void updateLocation(UUID customerId, double latitude, double longitude) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng: " + customerId));
        customer.updateLocation(latitude, longitude, Instant.now());
        customerRepository.save(customer);
    }
}
