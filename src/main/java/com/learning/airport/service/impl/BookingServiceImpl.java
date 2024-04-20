package com.learning.airport.service.impl;

import com.learning.airport.entity.BookingEntity;
import com.learning.airport.repository.BookingRepository;
import com.learning.airport.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final RedisTemplate<Object, Object> redisTemplate;

    @Override
    public BookingEntity createBooking(String flightNo, String seatNo) {
        String lockKey = flightNo + "_" + seatNo;
        Boolean distributedLock = redisTemplate.opsForValue()
                .setIfPresent(lockKey, 1, Duration.ofSeconds(10));
        if (Boolean.TRUE.equals(distributedLock)) {
            log.info("Seat {} on flight {} is being booked", seatNo, flightNo);
            return null;
        }
        if (bookingRepository.countByFlightNoAndAndSeatNo(flightNo, seatNo) > 0) {
            log.info("Seat {} on flight {} has been booked", seatNo, flightNo);
            return null;
        }
        BookingEntity booking = bookingRepository.save(BookingEntity.createNewBooking(flightNo, seatNo));
        log.info("Seat {} on flight {} has been booked successfully", seatNo, flightNo);
        redisTemplate.delete(lockKey);
        return booking;
    }
}
