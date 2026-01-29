package com.example.flightrebooking.dto;

import jakarta.validation.constraints.NotBlank;

public record RebookRequest(
    @NotBlank(message = "selectedFlightId is required")
    String selectedFlightId
) {}
