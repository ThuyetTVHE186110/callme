package com.callme.identity.dto;

public record RegisterCustomerRequest(String fullName, String phoneNumber, String email) {
}
