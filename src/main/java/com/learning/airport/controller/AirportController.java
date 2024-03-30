package com.learning.airport.controller;

import com.learning.airport.entity.AirportEntity;
import com.learning.airport.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/airports")
public class AirportController {
    private final AirportService airportService;

    @GetMapping("/{airportCode}")
    public ResponseEntity<AirportEntity> detail(@PathVariable String airportCode) {
        return ResponseEntity.ok(airportService.detail(airportCode));
    }
}
