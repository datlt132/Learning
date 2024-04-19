package com.learning.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private boolean success;
    private String token;

    public static LoginResponseDto notAuthenticate() {
        return LoginResponseDto.builder()
                .success(false)
                .build();
    }

    public static LoginResponseDto authenticated(String token) {
        return LoginResponseDto.builder()
                .success(true)
                .token(token)
                .build();
    }
}
