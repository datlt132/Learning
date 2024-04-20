package com.learning.airport.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "bookings")
public class BookingEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String seatNo;
    private String flightNo;

    public static BookingEntity createNewBooking(String flightNo, String seatNo) {
        BookingEntity booking = new BookingEntity();
        booking.setFlightNo(flightNo);
        booking.setSeatNo(seatNo);
        return booking;
    }

}
