package com.learning.airport.entity;

import com.learning.airport.converter.MultiLanguageConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "airports_data")
public class AirportEntity implements Serializable {
    @Id
    private String airportCode;

    @Convert(converter = MultiLanguageConverter.class)
    private MultiLanguage airportName;

    @Convert(converter = MultiLanguageConverter.class)
    private MultiLanguage city;

    private String timezone;

}
