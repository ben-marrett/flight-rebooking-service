package com.example.flightrebooking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flightrebooking")
            .withUsername("flight")
            .withPassword("flight");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetData() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Reset bookings to seed data state
            stmt.execute("DELETE FROM rebooking_audit");
            stmt.execute("UPDATE bookings SET status = 'DISRUPTED', rebooked_flight_id = NULL, version = 1 WHERE reference IN ('BK-001', 'BK-003', 'BK-004')");
            stmt.execute("UPDATE bookings SET status = 'CONFIRMED', rebooked_flight_id = NULL, version = 1 WHERE reference = 'BK-002'");
            stmt.execute("UPDATE bookings SET status = 'REBOOKED', version = 1 WHERE reference = 'BK-005'");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bookings/{ref}")
    class GetBooking {

        @Test
        @DisplayName("should return booking with disruption details")
        void shouldReturnBookingWithDisruptionDetails() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/BK-001"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("ETag"))
                    .andExpect(jsonPath("$.reference").value("BK-001"))
                    .andExpect(jsonPath("$.status").value("DISRUPTED"))
                    .andExpect(jsonPath("$.passengerName").value("Alice Johnson"))
                    .andExpect(jsonPath("$.originalFlight.flightNumber").value("NZ101"))
                    .andExpect(jsonPath("$.disruption.type").value("CANCELLATION"))
                    .andExpect(jsonPath("$.disruption.reasonCode").value("WX"));
        }

        @Test
        @DisplayName("should return 404 for non-existent booking")
        void shouldReturn404ForNonExistentBooking() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/BK-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Booking not found"))
                    .andExpect(jsonPath("$.detail").value("No booking found with reference BK-999"));
        }

        @Test
        @DisplayName("should return 400 for invalid booking reference format")
        void shouldReturn400ForInvalidBookingReference() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/invalid!ref"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Bad Request"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bookings/{ref}/rebooking-options")
    class GetRebookingOptions {

        @Test
        @DisplayName("should return scored options for disrupted booking (Scenario 1)")
        void shouldReturnScoredOptionsForDisruptedBooking() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/BK-001/rebooking-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingReference").value("BK-001"))
                    .andExpect(jsonPath("$.options").isArray())
                    .andExpect(jsonPath("$.options.length()").value(lessThanOrEqualTo(5)))
                    .andExpect(jsonPath("$.options[0].score").isNumber())
                    .andExpect(jsonPath("$.options[0].reason").isString());
        }

        @Test
        @DisplayName("should return 409 for confirmed booking (Scenario 5)")
        void shouldReturn409ForConfirmedBooking() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/BK-002/rebooking-options"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.detail").value(containsString("CONFIRMED")));
        }

        @Test
        @DisplayName("should return next-day options when no same-day available (Scenario 2)")
        void shouldReturnNextDayOptionsWhenNoSameDayAvailable() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/BK-003/rebooking-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.options").isArray())
                    .andExpect(jsonPath("$.options[0].reason").value(containsString("Next day")));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bookings/{ref}/rebook")
    class Rebook {

        @Test
        @DisplayName("should rebook successfully with valid request (Scenario 1)")
        void shouldRebookSuccessfully() throws Exception {
            String idempotencyKey = UUID.randomUUID().toString();

            // First, get the available options to find a valid flight ID
            String optionsResponse = mockMvc.perform(get("/api/v1/bookings/BK-001/rebooking-options"))
                    .andReturn().getResponse().getContentAsString();

            // Extract the first flight ID from options (simplistic parsing)
            String flightId = extractFlightIdFromOptions(optionsResponse);

            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bookingReference").value("BK-001"))
                    .andExpect(jsonPath("$.status").value("REBOOKED"))
                    .andExpect(jsonPath("$.newFlight").exists());
        }

        @Test
        @DisplayName("should return 200 for idempotent replay (Scenario 4)")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            String idempotencyKey = UUID.randomUUID().toString();

            // Get valid flight ID
            String optionsResponse = mockMvc.perform(get("/api/v1/bookings/BK-001/rebooking-options"))
                    .andReturn().getResponse().getContentAsString();
            String flightId = extractFlightIdFromOptions(optionsResponse);

            // First request - should be 201
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isCreated());

            // Second request with same key - should be 200
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingReference").value("BK-001"))
                    .andExpect(jsonPath("$.status").value("REBOOKED"));
        }

        @Test
        @DisplayName("should return 409 when already rebooked with different key (Scenario 4)")
        void shouldReturn409WhenAlreadyRebookedWithDifferentKey() throws Exception {
            String idempotencyKey1 = UUID.randomUUID().toString();
            String idempotencyKey2 = UUID.randomUUID().toString();

            // Get valid flight ID
            String optionsResponse = mockMvc.perform(get("/api/v1/bookings/BK-001/rebooking-options"))
                    .andReturn().getResponse().getContentAsString();
            String flightId = extractFlightIdFromOptions(optionsResponse);

            // First rebook
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", idempotencyKey1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isCreated());

            // Second rebook with different key - should be 409
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", idempotencyKey2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("already been rebooked")));
        }

        @Test
        @DisplayName("should return 400 for missing idempotency key")
        void shouldReturn400ForMissingIdempotencyKey() throws Exception {
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"some-flight\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
        }

        @Test
        @DisplayName("should return 409 for already rebooked booking (Scenario 5)")
        void shouldReturn409ForAlreadyRebookedBooking() throws Exception {
            mockMvc.perform(post("/api/v1/bookings/BK-005/rebook")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"some-flight\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 400 for invalid flight selection")
        void shouldReturn400ForInvalidFlightSelection() throws Exception {
            mockMvc.perform(post("/api/v1/bookings/BK-001/rebook")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"invalid-flight-id\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("not a valid rebooking option")));
        }

        @Test
        @DisplayName("should return 409 for stale ETag (Scenario 6)")
        void shouldReturn409ForStaleETag() throws Exception {
            String idempotencyKey = UUID.randomUUID().toString();

            // Get valid flight ID
            String optionsResponse = mockMvc.perform(get("/api/v1/bookings/BK-004/rebooking-options"))
                    .andReturn().getResponse().getContentAsString();
            String flightId = extractFlightIdFromOptions(optionsResponse);

            // Attempt with stale version
            mockMvc.perform(post("/api/v1/bookings/BK-004/rebook")
                            .header("Idempotency-Key", idempotencyKey)
                            .header("If-Match", "\"999\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedFlightId\": \"" + flightId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("modified by another request")));
        }

        private String extractFlightIdFromOptions(String json) {
            // Simple extraction - find first flightId value
            int idx = json.indexOf("\"flightId\":\"");
            if (idx == -1) return "unknown";
            int start = idx + 12;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
    }
}
