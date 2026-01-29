package com.example.flightrebooking.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rebooking_audit")
public class RebookingAudit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_flight_id", nullable = false)
    private Flight previousFlight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_flight_id", nullable = false)
    private Flight newFlight;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private RebookingOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RebookingAudit() {}

    public RebookingAudit(UUID id, Booking booking, UUID idempotencyKey,
                          Flight previousFlight, Flight newFlight,
                          RebookingOutcome outcome, String responsePayload) {
        this.id = id;
        this.booking = booking;
        this.idempotencyKey = idempotencyKey;
        this.previousFlight = previousFlight;
        this.newFlight = newFlight;
        this.outcome = outcome;
        this.responsePayload = responsePayload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Flight getPreviousFlight() {
        return previousFlight;
    }

    public Flight getNewFlight() {
        return newFlight;
    }

    public RebookingOutcome getOutcome() {
        return outcome;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
