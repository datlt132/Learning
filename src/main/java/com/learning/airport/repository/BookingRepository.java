package com.learning.airport.repository;

import com.learning.airport.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    long countByFlightNoAndAndSeatNo(String flightNo, String seatNo);
}
