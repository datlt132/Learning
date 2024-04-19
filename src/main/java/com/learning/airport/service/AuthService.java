package com.learning.airport.service;

import com.learning.airport.dto.LoginResponseDto;


public interface AuthService {
    LoginResponseDto login(String username, String password);
}
