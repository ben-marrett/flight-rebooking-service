package com.example.flightrebooking.exception;

import java.util.UUID;

public class IdempotencyKeyReusedException extends RuntimeException {

    private final UUID idempotencyKey;

    public IdempotencyKeyReusedException(UUID idempotencyKey) {
        super("Idempotency key has already been used for a different booking");
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
