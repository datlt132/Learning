package com.learning.airport.service;

import com.learning.airport.security.CustomUserDetails;


public interface JwtService {
    String generateToken(CustomUserDetails userDetails);

    boolean validateToken(String authToken);

    String getUsernameFromToken(String token);
}
