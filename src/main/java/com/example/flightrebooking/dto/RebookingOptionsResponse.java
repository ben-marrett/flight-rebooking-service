package com.example.flightrebooking.dto;

import java.time.Instant;
import java.util.List;

public record RebookingOptionsResponse(
    String bookingReference,
    Instant generatedAt,
    List<RebookingOptionResponse> options
) {}
