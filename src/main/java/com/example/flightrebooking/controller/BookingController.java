package com.example.flightrebooking.controller;

import com.example.flightrebooking.dto.BookingResponse;
import com.example.flightrebooking.entity.Booking;
import com.example.flightrebooking.exception.BookingNotFoundException;
import com.example.flightrebooking.repository.BookingRepository;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private static final String BOOKING_REF_PATTERN = "^[A-Z0-9-]{3,20}$";
    private static final String BOOKING_REF_MESSAGE = "Booking reference must be 3-20 alphanumeric characters or hyphens";

    private final BookingRepository bookingRepository;

    public BookingController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/{ref}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable("ref")
            @Pattern(regexp = BOOKING_REF_PATTERN, message = BOOKING_REF_MESSAGE)
            String ref) {

        Booking booking = bookingRepository.findByReferenceWithDetails(ref)
            .orElseThrow(() -> new BookingNotFoundException(ref));

        BookingResponse response = BookingResponse.from(booking);

        return ResponseEntity.ok()
            .eTag("\"" + booking.getVersion() + "\"")
            .body(response);
    }
}
