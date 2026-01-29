package com.example.flightrebooking.repository;

import com.example.flightrebooking.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID> {

    @Query("SELECT f FROM Flight f " +
           "WHERE f.origin = :origin " +
           "AND f.destination = :destination " +
           "AND f.scheduledDeparture > :after " +
           "ORDER BY f.scheduledDeparture")
    List<Flight> findAvailableFlights(String origin, String destination, Instant after);
}
