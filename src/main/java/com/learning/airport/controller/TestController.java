package com.learning.airport.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {


    @GetMapping("/view")
    @PreAuthorize("@authorizationService.hasPermission('VIEW')")
    public ResponseEntity<String> view() {
        return ResponseEntity.ok("User can view this resource!");
    }

    @PostMapping("/edit")
    @PreAuthorize("@authorizationService.hasPermission('EDIT')")
    public ResponseEntity<String> edit() {
        return ResponseEntity.ok("User can edit this resource!");
    }

}
