package com.example.flightrebooking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_flight_id", nullable = false)
    private Flight originalFlight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rebooked_flight_id")
    private Flight rebookedFlight;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Disruption disruption;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {}

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public Flight getOriginalFlight() {
        return originalFlight;
    }

    public Flight getRebookedFlight() {
        return rebookedFlight;
    }

    public void setRebookedFlight(Flight rebookedFlight) {
        this.rebookedFlight = rebookedFlight;
    }

    public Disruption getDisruption() {
        return disruption;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking booking)) return false;
        return Objects.equals(reference, booking.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }
}
