package com.learning.airport.controller;

import com.learning.airport.entity.AirportEntity;
import com.learning.airport.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/airports")
public class AirportController {
    private final AirportService airportService;

    @GetMapping("/{airportCode}")
    public ResponseEntity<AirportEntity> getDetail(@PathVariable String airportCode) {
        return ResponseEntity.ok(airportService.getDetail(airportCode));
    }

    @GetMapping()
    public ResponseEntity<List<AirportEntity>> getListAirport(@RequestParam List<Object> airportCodes) {
        return ResponseEntity.ok(airportService.getListAirport(airportCodes));
    }

    @PostMapping("")
    public ResponseEntity<AirportEntity> saveAirport(@RequestBody AirportEntity airport) {
        return ResponseEntity.ok(airportService.saveAirport(airport));
    }
}
