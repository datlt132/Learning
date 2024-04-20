package com.learning.airport.controller;

import com.learning.airport.entity.BookingEntity;
import com.learning.airport.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/booking")
@RestController
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping("/flights/{flightNo}/seats/{seatNo}")
    public ResponseEntity<BookingEntity> createBooking(@PathVariable String flightNo, @PathVariable String seatNo) {
        return ResponseEntity.ok(bookingService.createBooking(flightNo, seatNo));
    }
}
