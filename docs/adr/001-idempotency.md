# ADR 001: Idempotency Implementation for Rebooking Operations

## Status

Accepted

## Context

The flight rebooking operation (`POST /api/v1/bookings/{ref}/rebook`) is a critical, non-reversible action that transitions a booking from `DISRUPTED` to `REBOOKED` state. In production environments, this operation may be retried due to:

- Network failures causing ambiguous responses
- Client timeouts before server response
- Load balancer retries
- User double-clicks or refresh actions

Without idempotency guarantees, retries could cause:
- Duplicate audit records
- Inconsistent state if the booking was already processed
- Confusion about whether the operation succeeded

## Decision

We implement client-controlled idempotency using an `Idempotency-Key` header:

### Mechanism

1. **Client provides UUID**: Every `POST /rebook` request must include an `Idempotency-Key` header containing a UUID
2. **Server stores outcome**: After successful processing, the response is stored in `rebooking_audit` table keyed by the idempotency key
3. **Replay on duplicate**: If the same key is received again for the same booking, return the stored response with HTTP 200 (not 201)
4. **Reject key reuse**: If the same key is used for a different booking, return HTTP 400

### Response Codes

| Scenario | HTTP Status |
|----------|-------------|
| First successful rebook | 201 Created |
| Replay (same key, same booking) | 200 OK |
| Key reused for different booking | 400 Bad Request |
| Booking already rebooked (different key) | 409 Conflict |

### Storage Schema

```sql
CREATE TABLE rebooking_audit (
    id UUID PRIMARY KEY,
    booking_id UUID REFERENCES bookings(id),
    idempotency_key UUID UNIQUE,
    previous_flight_id UUID,
    new_flight_id UUID,
    outcome rebooking_outcome,
    response_payload JSONB,  -- Full response for replay
    created_at TIMESTAMP
);
```

## Consequences

### Positive

- **Safe retries**: Clients can safely retry failed requests without risk of duplicate actions
- **Audit trail**: Every rebooking attempt is recorded with full response payload
- **Clear semantics**: HTTP status codes clearly indicate first execution vs replay

### Negative

- **Storage growth**: Audit table grows indefinitely (mitigated by TTL in production)
- **Complexity**: Additional validation logic in request path
- **Client burden**: Clients must generate and track UUIDs

### Trade-offs Considered

1. **Server-generated keys vs client-provided**: Client-provided chosen for transparency and client control over retry semantics
2. **Response storage**: Full JSON payload stored to ensure byte-identical responses on replay
3. **Key format**: UUID chosen over arbitrary strings for consistency and collision avoidance

## Future Considerations

- **TTL expiry**: Production should expire idempotency keys after 24-48 hours
- **Distributed locking**: Current implementation assumes single database; distributed systems need additional coordination
- **Partial failure handling**: Consider storing outcome even for failed operations to prevent retry divergence
