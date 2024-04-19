package com.learning.airport.service.impl;

import com.learning.airport.dto.LoginResponseDto;
import com.learning.airport.repository.UserRepository;
import com.learning.airport.security.CustomUserDetails;
import com.learning.airport.service.AuthService;
import com.learning.airport.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public LoginResponseDto login(String username, String password) {
        log.info("Login user-system by username = [{}]", username);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException exception) {
            exception.printStackTrace();
            return LoginResponseDto.notAuthenticate();
        }
        if (!authentication.isAuthenticated()) {
            return LoginResponseDto.notAuthenticate();
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtService.generateToken((CustomUserDetails) authentication.getPrincipal());
        return LoginResponseDto.authenticated(token);
    }
}
