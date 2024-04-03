package com.learning.airport.service;

import com.learning.airport.entity.AirportEntity;
import com.learning.airport.repository.AirportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {
    private static final String AIRPORTS_KEY = "airports_key";
    private final AirportRepository airportRepository;
    private final RedisTemplate<Object, Object> redisTemplate;

    @Override
    public AirportEntity getDetail(String airportCode) {
        Object airportFromCache = redisTemplate.opsForHash().get(AIRPORTS_KEY, airportCode);
        if (airportFromCache != null) {
            log.info("Cache hit with key: " + airportCode);
            return (AirportEntity) airportFromCache;
        }

        log.info("Cache miss with key: " + airportCode);
        AirportEntity airport = airportRepository.findById(airportCode)
                .orElseThrow(EntityNotFoundException::new);
        redisTemplate.opsForHash().putIfAbsent(AIRPORTS_KEY, airportCode, airport);
        return airport;
    }

    @Override
    public List<AirportEntity> getListAirport(List<Object> airportCodes) {
        List<Object> airportFromCache = redisTemplate.opsForHash().multiGet(AIRPORTS_KEY, airportCodes);
        List<AirportEntity> cachedAirports = airportFromCache.stream()
                .filter(Objects::nonNull)
                .map(a -> (AirportEntity) a)
                .toList();
        List<String> cachedAirportCodes = cachedAirports.stream().map(AirportEntity::getAirportCode).toList();
        log.info("Cache hit with keys: {}", cachedAirportCodes);
        List<String> nonCachedAirportCodes = airportCodes.stream()
                .map(String.class::cast)
                .filter(code -> !cachedAirportCodes.contains(code))
                .toList();
        if (nonCachedAirportCodes.isEmpty()) {
            return cachedAirports;
        }

        log.info("Cache miss with keys: {}", nonCachedAirportCodes);
        List<AirportEntity> airports = airportRepository.findAllById(nonCachedAirportCodes);
        Map<String, AirportEntity> mapAirportByCode = airports.stream()
                .collect(Collectors.toMap(AirportEntity::getAirportCode, Function.identity()));
        redisTemplate.opsForHash().putAll(AIRPORTS_KEY, mapAirportByCode);
        airports.addAll(cachedAirports);
        return airports;
    }

    @Override
    public AirportEntity saveAirport(AirportEntity airport) {
        AirportEntity savedAirport = airportRepository.save(airport);
        redisTemplate.opsForHash().put(AIRPORTS_KEY, savedAirport.getAirportCode(), airport);
        return savedAirport;
    }

}
