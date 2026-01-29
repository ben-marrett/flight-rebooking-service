package com.example.flightrebooking.dto;

public record RebookResult(
    RebookResponse response,
    boolean isReplay
) {
    public static RebookResult newRebook(RebookResponse response) {
        return new RebookResult(response, false);
    }

    public static RebookResult replay(RebookResponse response) {
        return new RebookResult(response, true);
    }
}
