package com.example.flightrebooking.exception;

public class BookingNotFoundException extends RuntimeException {

    private final String reference;

    public BookingNotFoundException(String reference) {
        super("No booking found with reference " + reference);
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
