package com.learning.airport.service;

import com.learning.airport.entity.AirportEntity;

import java.util.List;

public interface AirportService {
    AirportEntity getDetail(String airportCode);

    List<AirportEntity> getListAirport(List<Object> airportCodes);

    AirportEntity saveAirport(AirportEntity airport);
}
