package com.example.flightrebooking.exception;

public class ETagMismatchException extends RuntimeException {

    public ETagMismatchException() {
        super("Booking was modified by another request; please retry");
    }
}
