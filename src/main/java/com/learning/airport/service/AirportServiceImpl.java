package com.learning.airport.service;

import com.learning.airport.entity.AirportEntity;
import com.learning.airport.repository.AirportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {
    private final AirportRepository airportRepository;
    private final RedisTemplate<Object, Object> redisTemplate;

    @Override
    public AirportEntity detail(String airportCode) {
        Object airportFromCache = redisTemplate.opsForValue().get(airportCode);
        if (airportFromCache != null) {
            log.info("Cache hit with key: " + airportCode);
            Long expireTime = redisTemplate.getExpire(airportCode, TimeUnit.SECONDS);
            log.info("Cache with key {} expire in: {}s", airportCode, expireTime);
            return (AirportEntity) airportFromCache;
        }
        log.info("Cache miss with key: " + airportCode);
        AirportEntity airport = airportRepository.findById(airportCode).orElseThrow();
        redisTemplate.opsForValue().set(airportCode, airport);
        redisTemplate.expire(airportCode, 30, TimeUnit.SECONDS);
        Long expireTime = redisTemplate.getExpire(airportCode, TimeUnit.SECONDS);
        log.info("Cache with key {} expire in: {}s", airportCode, expireTime);
        return airport;
    }

}
