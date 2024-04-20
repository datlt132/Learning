package com.learning.airport.service;

import com.learning.airport.entity.BookingEntity;

public interface BookingService {
    BookingEntity createBooking(String flightNo, String seatNo);
}
