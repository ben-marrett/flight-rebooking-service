package com.example.flightrebooking.service;

import com.example.flightrebooking.dto.FlightResponse;
import com.example.flightrebooking.dto.RebookingOptionResponse;
import com.example.flightrebooking.dto.RebookingOptionsResponse;
import com.example.flightrebooking.entity.Booking;
import com.example.flightrebooking.entity.BookingStatus;
import com.example.flightrebooking.entity.Flight;
import com.example.flightrebooking.exception.BookingNotEligibleException;
import com.example.flightrebooking.exception.BookingNotFoundException;
import com.example.flightrebooking.repository.BookingRepository;
import com.example.flightrebooking.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.List;

@Service
public class RebookingService {

    private static final int MAX_OPTIONS = 5;

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;

    public RebookingService(BookingRepository bookingRepository, FlightRepository flightRepository) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
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
}
