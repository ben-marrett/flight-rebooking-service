package com.example.flightrebooking.dto;

import com.example.flightrebooking.entity.Flight;
import java.time.Instant;

public record FlightResponse(
    String flightId,
    String flightNumber,
    String origin,
    String destination,
    Instant scheduledDeparture
) {
    public static FlightResponse from(Flight flight) {
        if (flight == null) return null;
        return new FlightResponse(
            flight.getId().toString(),
            flight.getFlightNumber(),
            flight.getOrigin(),
            flight.getDestination(),
            flight.getScheduledDeparture()
        );
    }
}
