package com.example.flightrebooking.exception;

public class InvalidFlightSelectionException extends RuntimeException {

    private final String flightId;

    public InvalidFlightSelectionException(String flightId) {
        super(String.format("Flight %s is not a valid rebooking option for this booking", flightId));
        this.flightId = flightId;
    }

    public String getFlightId() {
        return flightId;
    }
}
