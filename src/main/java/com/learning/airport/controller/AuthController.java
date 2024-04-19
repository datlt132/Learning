package com.learning.airport.controller;

import com.learning.airport.dto.LoginResponseDto;
import com.learning.airport.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @GetMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestParam String username,
                                                  @RequestParam String password) {
        return ResponseEntity.ok().body(authService.login(username, password));
    }
}
