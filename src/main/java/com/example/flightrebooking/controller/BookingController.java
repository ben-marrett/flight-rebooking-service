package com.example.flightrebooking.controller;

import com.example.flightrebooking.dto.BookingResponse;
import com.example.flightrebooking.dto.RebookRequest;
import com.example.flightrebooking.dto.RebookResult;
import com.example.flightrebooking.dto.RebookingOptionsResponse;
import com.example.flightrebooking.entity.Booking;
import com.example.flightrebooking.exception.BookingNotFoundException;
import com.example.flightrebooking.exception.ETagMismatchException;
import com.example.flightrebooking.repository.BookingRepository;
import com.example.flightrebooking.service.RebookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private static final String BOOKING_REF_PATTERN = "^[A-Z0-9-]{3,20}$";
    private static final String BOOKING_REF_MESSAGE = "Booking reference must be 3-20 alphanumeric characters or hyphens";

    private final BookingRepository bookingRepository;
    private final RebookingService rebookingService;

    public BookingController(BookingRepository bookingRepository, RebookingService rebookingService) {
        this.bookingRepository = bookingRepository;
        this.rebookingService = rebookingService;
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

    @GetMapping("/{ref}/rebooking-options")
    public RebookingOptionsResponse getRebookingOptions(
            @PathVariable("ref")
            @Pattern(regexp = BOOKING_REF_PATTERN, message = BOOKING_REF_MESSAGE)
            String ref) {

        return rebookingService.getRebookingOptions(ref);
    }

    @PostMapping("/{ref}/rebook")
    public ResponseEntity<?> rebook(
            @PathVariable("ref")
            @Pattern(regexp = BOOKING_REF_PATTERN, message = BOOKING_REF_MESSAGE)
            String ref,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
            @Valid @RequestBody RebookRequest request) {

        // Validate idempotency key
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key header is required and must be a valid UUID"
            );
            problem.setTitle("Bad Request");
            return ResponseEntity.badRequest().body(problem);
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        } catch (IllegalArgumentException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key header is required and must be a valid UUID"
            );
            problem.setTitle("Bad Request");
            return ResponseEntity.badRequest().body(problem);
        }

        // Parse If-Match header (optional)
        Long expectedVersion = null;
        if (ifMatchHeader != null && !ifMatchHeader.isBlank()) {
            String versionStr = ifMatchHeader.replace("\"", "");
            try {
                expectedVersion = Long.parseLong(versionStr);
            } catch (NumberFormatException e) {
                // Invalid format, ignore
            }
        }

        RebookResult result = rebookingService.rebook(
            ref,
            request.selectedFlightId(),
            idempotencyKey,
            expectedVersion
        );

        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }
}
