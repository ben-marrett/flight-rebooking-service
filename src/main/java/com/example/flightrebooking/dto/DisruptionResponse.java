package com.example.flightrebooking.dto;

import com.example.flightrebooking.entity.Disruption;
import java.time.Instant;

public record DisruptionResponse(
    String type,
    String reasonCode,
    String reasonDescription,
    Instant occurredAt
) {
    public static DisruptionResponse from(Disruption disruption) {
        if (disruption == null) return null;
        return new DisruptionResponse(
            disruption.getType().name(),
            disruption.getReasonCode(),
            disruption.getReasonDescription(),
            disruption.getOccurredAt()
        );
    }
}
