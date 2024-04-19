package com.learning.airport.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Permission {
    EDIT("edit"),
    VIEW("view"),
    NO_PERMISSION("no_permission"),
    ;
    private final String value;
}
