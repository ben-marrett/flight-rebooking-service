package com.example.flightrebooking.dto;

public record RebookingOptionResponse(
    FlightResponse flight,
    int score,
    String reason
) {}
