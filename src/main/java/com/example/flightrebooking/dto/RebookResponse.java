package com.example.flightrebooking.dto;

import java.time.Instant;

public record RebookResponse(
    String bookingReference,
    String status,
    FlightResponse previousFlight,
    FlightResponse newFlight,
    Instant rebookedAt
) {}
