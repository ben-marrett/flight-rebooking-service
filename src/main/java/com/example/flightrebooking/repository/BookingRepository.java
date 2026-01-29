package com.example.flightrebooking.repository;

import com.example.flightrebooking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.originalFlight " +
           "LEFT JOIN FETCH b.rebookedFlight " +
           "LEFT JOIN FETCH b.disruption " +
           "WHERE b.reference = :reference")
    Optional<Booking> findByReferenceWithDetails(String reference);
}
