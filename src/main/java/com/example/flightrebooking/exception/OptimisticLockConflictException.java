package com.example.flightrebooking.exception;

public class OptimisticLockConflictException extends RuntimeException {

    public OptimisticLockConflictException() {
        super("Booking was modified by another request; please retry");
    }
}
