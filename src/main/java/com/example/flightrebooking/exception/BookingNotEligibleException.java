package com.example.flightrebooking.exception;

import com.example.flightrebooking.entity.BookingStatus;

public class BookingNotEligibleException extends RuntimeException {

    private final String reference;
    private final BookingStatus currentStatus;

    public BookingNotEligibleException(String reference, BookingStatus currentStatus) {
        super(String.format("Booking %s is in %s state; only DISRUPTED bookings can be rebooked",
            reference, currentStatus));
        this.reference = reference;
        this.currentStatus = currentStatus;
    }

    public String getReference() {
        return reference;
    }

    public BookingStatus getCurrentStatus() {
        return currentStatus;
    }
}
