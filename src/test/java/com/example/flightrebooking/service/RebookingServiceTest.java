package com.example.flightrebooking.service;

import com.example.flightrebooking.entity.Flight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RebookingServiceTest {

    private RebookingService rebookingService;

    @BeforeEach
    void setUp() {
        // Create service with null dependencies - we only test scoring methods
        rebookingService = new RebookingService(null, null, null, null);
    }

    @Nested
    @DisplayName("Scoring Algorithm")
    class ScoringAlgorithm {

        @Test
        @DisplayName("should give base score of 100 for same day, same time flight")
        void shouldGiveBaseScoreForSameDaySameTime() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(originalTime.plus(30, ChronoUnit.MINUTES));

            int score = rebookingService.calculateScore(candidate, original);

            // Base 100 + 10 bonus for similar time (within 2 hours) - small delay penalty
            assertTrue(score >= 100, "Same day, similar time should score at least 100");
        }

        @Test
        @DisplayName("should penalize 30 points for different day")
        void shouldPenalize30PointsForDifferentDay() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Instant nextDayTime = Instant.parse("2026-06-16T08:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(nextDayTime);

            int score = rebookingService.calculateScore(candidate, original);

            // Base 100 - 30 (different day) - 40 (24h delay, capped) + 10 (similar time) = 40
            assertTrue(score < 100, "Different day should score less than 100");
            assertTrue(score >= 0, "Score should never be negative");
            assertEquals(40, score, "Next day same time should score 40");
        }

        @Test
        @DisplayName("should penalize 5 points per hour of delay, capped at 40")
        void shouldPenalizeDelayWithCap() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);

            // 4 hours delay = 20 points penalty
            Flight candidate4h = createFlight(originalTime.plus(4, ChronoUnit.HOURS));
            int score4h = rebookingService.calculateScore(candidate4h, original);

            // 10 hours delay = 40 points penalty (capped)
            Flight candidate10h = createFlight(originalTime.plus(10, ChronoUnit.HOURS));
            int score10h = rebookingService.calculateScore(candidate10h, original);

            // 4h delay should score higher than 10h delay
            assertTrue(score4h > score10h, "4h delay should score higher than 10h delay");

            // 12 hours delay - should still be capped at 40
            Flight candidate12h = createFlight(originalTime.plus(12, ChronoUnit.HOURS));
            int score12h = rebookingService.calculateScore(candidate12h, original);

            // 10h and 12h should have similar scores (both hit the cap)
            assertEquals(score10h, score12h, "Delay penalty should be capped at 40");
        }

        @Test
        @DisplayName("should give 10 point bonus for similar departure time (within 2 hours)")
        void shouldGiveBonusForSimilarDepartureTime() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);

            // Same time of day, next day
            Flight candidateSameTime = createFlight(Instant.parse("2026-06-16T08:00:00Z"));
            int scoreSameTime = rebookingService.calculateScore(candidateSameTime, original);

            // Different time of day, next day (6 hours different)
            Flight candidateDifferentTime = createFlight(Instant.parse("2026-06-16T14:00:00Z"));
            int scoreDifferentTime = rebookingService.calculateScore(candidateDifferentTime, original);

            // Same time of day should score higher due to bonus
            assertTrue(scoreSameTime > scoreDifferentTime,
                    "Same time of day should score higher than different time");
        }

        @Test
        @DisplayName("should never return negative score")
        void shouldNeverReturnNegativeScore() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);

            // Extreme case: 30 days later, completely different time
            Flight candidateExtreme = createFlight(Instant.parse("2026-07-15T22:00:00Z"));
            int score = rebookingService.calculateScore(candidateExtreme, original);

            assertTrue(score >= 0, "Score should never be negative");
        }

        @Test
        @DisplayName("same-day flight should score higher than next-day flight (Scenario 3)")
        void sameDayFlightShouldScoreHigherThanNextDay() {
            Instant originalTime = Instant.parse("2026-06-15T12:00:00Z");
            Flight original = createFlight(originalTime);

            // Same day, 2 hours later
            Flight sameDayFlight = createFlight(Instant.parse("2026-06-15T14:00:00Z"));

            // Next day, same time
            Flight nextDayFlight = createFlight(Instant.parse("2026-06-16T08:00:00Z"));

            int sameDayScore = rebookingService.calculateScore(sameDayFlight, original);
            int nextDayScore = rebookingService.calculateScore(nextDayFlight, original);

            assertTrue(sameDayScore > nextDayScore,
                    "Same day flight should score higher than next day flight");
        }

        private Flight createFlight(Instant scheduledDeparture) {
            Flight flight = new Flight();
            flight.setId(UUID.randomUUID());
            flight.setFlightNumber("NZ999");
            flight.setOrigin("AKL");
            flight.setDestination("WLG");
            flight.setScheduledDeparture(scheduledDeparture);
            return flight;
        }
    }

    @Nested
    @DisplayName("Reason String Generation")
    class ReasonStringGeneration {

        @Test
        @DisplayName("should generate same-day similar time reason")
        void shouldGenerateSameDaySimilarTimeReason() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(originalTime.plus(1, ChronoUnit.HOURS));

            String reason = rebookingService.generateReason(candidate, original);

            assertTrue(reason.contains("Same day") && reason.contains("similar"),
                    "Should mention same day and similar time");
        }

        @Test
        @DisplayName("should generate same-day delay reason")
        void shouldGenerateSameDayDelayReason() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(originalTime.plus(6, ChronoUnit.HOURS));

            String reason = rebookingService.generateReason(candidate, original);

            assertTrue(reason.contains("Same day") && reason.contains("later"),
                    "Should mention same day and delay");
        }

        @Test
        @DisplayName("should generate next-day reason")
        void shouldGenerateNextDayReason() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Instant nextDayTime = Instant.parse("2026-06-16T10:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(nextDayTime);

            String reason = rebookingService.generateReason(candidate, original);

            assertTrue(reason.contains("Next day"), "Should mention next day");
        }

        @Test
        @DisplayName("should generate multiple days later reason")
        void shouldGenerateMultipleDaysLaterReason() {
            Instant originalTime = Instant.parse("2026-06-15T08:00:00Z");
            Instant twoDaysLater = Instant.parse("2026-06-17T14:00:00Z");
            Flight original = createFlight(originalTime);
            Flight candidate = createFlight(twoDaysLater);

            String reason = rebookingService.generateReason(candidate, original);

            assertTrue(reason.contains("days later"), "Should mention days later");
        }

        private Flight createFlight(Instant scheduledDeparture) {
            Flight flight = new Flight();
            flight.setId(UUID.randomUUID());
            flight.setFlightNumber("NZ999");
            flight.setOrigin("AKL");
            flight.setDestination("WLG");
            flight.setScheduledDeparture(scheduledDeparture);
            return flight;
        }
    }
}
