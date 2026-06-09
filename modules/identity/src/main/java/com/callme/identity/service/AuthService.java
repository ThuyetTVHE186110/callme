package com.callme.identity.service;

import com.callme.identity.dto.AuthResponse;
import com.callme.identity.dto.LoginRequest;
import com.callme.identity.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
