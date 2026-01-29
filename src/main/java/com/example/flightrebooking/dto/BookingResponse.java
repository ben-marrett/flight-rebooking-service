package com.example.flightrebooking.dto;

import com.example.flightrebooking.entity.Booking;

public record BookingResponse(
    String reference,
    String status,
    String passengerName,
    FlightResponse originalFlight,
    DisruptionResponse disruption,
    FlightResponse rebookedFlight,
    Long version
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
            booking.getReference(),
            booking.getStatus().name(),
            booking.getPassengerName(),
            FlightResponse.from(booking.getOriginalFlight()),
            DisruptionResponse.from(booking.getDisruption()),
            FlightResponse.from(booking.getRebookedFlight()),
            booking.getVersion()
        );
    }
}
