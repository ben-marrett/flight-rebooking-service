package com.example.flightrebooking.exception;

public class AlreadyRebookedException extends RuntimeException {

    private final String reference;

    public AlreadyRebookedException(String reference) {
        super(String.format("Booking %s has already been rebooked", reference));
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
