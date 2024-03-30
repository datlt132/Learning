package com.learning.airport.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.airport.entity.MultiLanguage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class MultiLanguageConverter implements AttributeConverter<MultiLanguage, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MultiLanguage multiLanguage) {
        try {
            return objectMapper.writeValueAsString(multiLanguage);
        } catch (JsonProcessingException jpe) {
            log.warn("Cannot convert MultiLanguage into JSON");
            return null;
        }
    }

    @Override
    public MultiLanguage convertToEntityAttribute(String value) {
        try {
            return objectMapper.readValue(value, MultiLanguage.class);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert JSON into MultiLanguage");
            return null;
        }
    }
}
