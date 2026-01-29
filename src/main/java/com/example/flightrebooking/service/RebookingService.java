package com.example.flightrebooking.service;

import com.example.flightrebooking.dto.FlightResponse;
import com.example.flightrebooking.dto.RebookingOptionResponse;
import com.example.flightrebooking.dto.RebookingOptionsResponse;
import com.example.flightrebooking.dto.RebookResponse;
import com.example.flightrebooking.dto.RebookResult;
import com.example.flightrebooking.entity.*;
import com.example.flightrebooking.exception.AlreadyRebookedException;
import com.example.flightrebooking.exception.BookingNotEligibleException;
import com.example.flightrebooking.exception.BookingNotFoundException;
import com.example.flightrebooking.exception.ETagMismatchException;
import com.example.flightrebooking.exception.IdempotencyKeyReusedException;
import com.example.flightrebooking.exception.InvalidFlightSelectionException;
import com.example.flightrebooking.repository.BookingRepository;
import com.example.flightrebooking.repository.FlightRepository;
import com.example.flightrebooking.repository.RebookingAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RebookingService {

    private static final int MAX_OPTIONS = 5;

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final RebookingAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public RebookingService(BookingRepository bookingRepository,
                           FlightRepository flightRepository,
                           RebookingAuditRepository auditRepository,
                           ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RebookingOptionsResponse getRebookingOptions(String reference) {
        Booking booking = bookingRepository.findByReferenceWithDetails(reference)
            .orElseThrow(() -> new BookingNotFoundException(reference));

        if (booking.getStatus() != BookingStatus.DISRUPTED) {
            throw new BookingNotEligibleException(reference, booking.getStatus());
        }

        Flight originalFlight = booking.getOriginalFlight();
        Instant disruptionTime = booking.getDisruption() != null
            ? booking.getDisruption().getOccurredAt()
            : originalFlight.getScheduledDeparture();

        List<Flight> availableFlights = flightRepository.findAvailableFlights(
            originalFlight.getOrigin(),
            originalFlight.getDestination(),
            disruptionTime
        );

        List<RebookingOptionResponse> options = availableFlights.stream()
            .filter(f -> !f.getId().equals(originalFlight.getId()))
            .map(f -> scoreAndCreateOption(f, originalFlight))
            .sorted(Comparator
                .comparingInt(RebookingOptionResponse::score).reversed()
                .thenComparing(o -> o.flight().scheduledDeparture()))
            .limit(MAX_OPTIONS)
            .toList();

        return new RebookingOptionsResponse(
            booking.getReference(),
            Instant.now(),
            options
        );
    }

    private RebookingOptionResponse scoreAndCreateOption(Flight candidate, Flight original) {
        int score = calculateScore(candidate, original);
        String reason = generateReason(candidate, original);
        return new RebookingOptionResponse(FlightResponse.from(candidate), score, reason);
    }

    int calculateScore(Flight candidate, Flight original) {
        int score = 100;

        LocalDate originalDate = toLocalDate(original.getScheduledDeparture());
        LocalDate candidateDate = toLocalDate(candidate.getScheduledDeparture());

        // Penalty for different day (30 points)
        if (!candidateDate.equals(originalDate)) {
            score -= 30;
        }

        // Delay penalty (5 points per hour, capped at 40)
        long delayMinutes = Duration.between(
            original.getScheduledDeparture(),
            candidate.getScheduledDeparture()
        ).toMinutes();

        if (delayMinutes > 0) {
            double delayHours = delayMinutes / 60.0;
            int delayPenalty = (int) Math.min(delayHours * 5, 40);
            score -= delayPenalty;
        }

        // Bonus for similar departure time (within 2 hours of original time-of-day)
        LocalTime originalTime = toLocalTime(original.getScheduledDeparture());
        LocalTime candidateTime = toLocalTime(candidate.getScheduledDeparture());
        long timeDiffMinutes = Math.abs(
            Duration.between(originalTime, candidateTime).toMinutes()
        );

        if (timeDiffMinutes <= 120) {
            score += 10;
        }

        return Math.max(score, 0);
    }

    String generateReason(Flight candidate, Flight original) {
        LocalDate originalDate = toLocalDate(original.getScheduledDeparture());
        LocalDate candidateDate = toLocalDate(candidate.getScheduledDeparture());
        LocalTime candidateTime = toLocalTime(candidate.getScheduledDeparture());

        long daysDiff = Period.between(originalDate, candidateDate).getDays();
        long hoursDiff = Duration.between(
            original.getScheduledDeparture(),
            candidate.getScheduledDeparture()
        ).toHours();

        String timeStr = String.format("%02d:%02d", candidateTime.getHour(), candidateTime.getMinute());

        if (daysDiff == 0) {
            // Same day
            LocalTime originalTime = toLocalTime(original.getScheduledDeparture());
            long timeDiffMinutes = Math.abs(
                Duration.between(originalTime, candidateTime).toMinutes()
            );

            if (timeDiffMinutes <= 120) {
                return "Same day, similar departure time, direct flight";
            } else {
                return String.format("Same day, %dh later than original, direct flight", hoursDiff);
            }
        } else if (daysDiff == 1) {
            return String.format("Next day, %s departure, direct flight", timeStr);
        } else {
            return String.format("%d days later, %s departure", daysDiff, timeStr);
        }
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private LocalTime toLocalTime(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalTime();
    }

    @Transactional
    public RebookResult rebook(String reference, String selectedFlightId, UUID idempotencyKey, Long expectedVersion) {
        // Check for existing idempotency key
        var existingAudit = auditRepository.findByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            RebookingAudit audit = existingAudit.get();
            // Check if it's for the same booking
            if (!audit.getBooking().getReference().equals(reference)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            // Return stored response (replay)
            RebookResponse storedResponse = deserializeResponse(audit.getResponsePayload());
            return RebookResult.replay(storedResponse);
        }

        Booking booking = bookingRepository.findByReferenceWithDetails(reference)
            .orElseThrow(() -> new BookingNotFoundException(reference));

        // Check If-Match header for optimistic concurrency
        if (expectedVersion != null && !expectedVersion.equals(booking.getVersion())) {
            throw new ETagMismatchException();
        }

        if (booking.getStatus() == BookingStatus.REBOOKED) {
            throw new AlreadyRebookedException(reference);
        }
        if (booking.getStatus() != BookingStatus.DISRUPTED) {
            throw new BookingNotEligibleException(reference, booking.getStatus());
        }

        // Validate selected flight is a valid option
        RebookingOptionsResponse options = getRebookingOptions(reference);
        boolean validSelection = options.options().stream()
            .anyMatch(opt -> opt.flight().flightId().equals(selectedFlightId));

        if (!validSelection) {
            throw new InvalidFlightSelectionException(selectedFlightId);
        }

        // Find the new flight
        UUID newFlightId = UUID.fromString(selectedFlightId);
        Flight newFlight = flightRepository.findById(newFlightId)
            .orElseThrow(() -> new InvalidFlightSelectionException(selectedFlightId));

        Flight previousFlight = booking.getOriginalFlight();
        Instant rebookedAt = Instant.now();

        // Update booking
        booking.setStatus(BookingStatus.REBOOKED);
        booking.setRebookedFlight(newFlight);
        booking.setUpdatedAt(rebookedAt);
        bookingRepository.save(booking);

        // Create response
        RebookResponse response = new RebookResponse(
            booking.getReference(),
            BookingStatus.REBOOKED.name(),
            FlightResponse.from(previousFlight),
            FlightResponse.from(newFlight),
            rebookedAt
        );

        // Create audit record
        String responseJson = serializeResponse(response);
        RebookingAudit audit = new RebookingAudit(
            UUID.randomUUID(),
            booking,
            idempotencyKey,
            previousFlight,
            newFlight,
            RebookingOutcome.SUCCESS,
            responseJson
        );
        auditRepository.save(audit);

        return RebookResult.newRebook(response);
    }

    private String serializeResponse(RebookResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private RebookResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, RebookResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize stored response", e);
        }
    }
}
