# Flight Rebooking Service

A production-aware backend service demonstrating safe state transitions, idempotent operations, and optimistic concurrency control for airline disruption recovery.

## Quick Start

```bash
# Start PostgreSQL
docker-compose up -d

# Run the application
./gradlew bootRun
```

The service will be available at `http://localhost:8080`

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health check**: http://localhost:8080/actuator/health

## API Examples

### 1. Get booking details

```bash
curl -s http://localhost:8080/api/v1/bookings/BK-001 | jq
```

Response:
```json
{
  "reference": "BK-001",
  "status": "DISRUPTED",
  "passengerName": "Test Passenger",
  "originalFlight": {
    "flightId": "...",
    "flightNumber": "NZ101",
    "origin": "AKL",
    "destination": "WLG",
    "scheduledDeparture": "2026-06-15T08:00:00Z"
  },
  "disruption": {
    "type": "CANCELLATION",
    "reasonCode": "WX",
    "reasonDescription": "Weather"
  }
}
```

### 2. Get rebooking options

```bash
curl -s http://localhost:8080/api/v1/bookings/BK-001/rebooking-options | jq
```

Response:
```json
{
  "bookingReference": "BK-001",
  "generatedAt": "2026-01-29T...",
  "options": [
    {
      "flight": {
        "flightNumber": "NZ103",
        "scheduledDeparture": "2026-06-15T14:00:00Z"
      },
      "score": 80,
      "reason": "Same day, 6h later than original, direct flight"
    }
  ]
}
```

### 3. Rebook to a new flight

```bash
curl -X POST http://localhost:8080/api/v1/bookings/BK-001/rebook \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"selectedFlightId": "<flight-id-from-options>"}'
```

Response (201 Created):
```json
{
  "bookingReference": "BK-001",
  "status": "REBOOKED",
  "previousFlight": { "flightNumber": "NZ101" },
  "newFlight": { "flightNumber": "NZ103" },
  "rebookedAt": "2026-01-29T..."
}
```

### 4. Error: Non-disrupted booking

```bash
curl -s http://localhost:8080/api/v1/bookings/BK-002/rebooking-options | jq
```

Response (409 Conflict):
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking BK-002 is in CONFIRMED state; only DISRUPTED bookings can be rebooked"
}
```

### 5. Error: Invalid booking reference

```bash
curl -s http://localhost:8080/api/v1/bookings/invalid! | jq
```

Response (400 Bad Request):
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Booking reference must be 3-20 alphanumeric characters or hyphens"
}
```

## Design

### State Machine

```
                    ┌──────────────┐
                    │   CONFIRMED  │
                    └──────┬───────┘
                           │
                    [disruption occurs]
                           │
                           ▼
                    ┌──────────────┐
         ┌─────────│   DISRUPTED  │─────────┐
         │         └──────────────┘         │
         │                                  │
    [rebook success]                  [cancel - future]
         │                                  │
         ▼                                  ▼
  ┌──────────────┐                   ┌──────────────┐
  │   REBOOKED   │                   │  CANCELLED   │
  │  (terminal)  │                   │  (terminal)  │
  └──────────────┘                   └──────────────┘
```

### Scoring Algorithm

Options are computed on-demand and scored based on:

| Factor | Impact |
|--------|--------|
| Different day | -30 points |
| Delay (per hour) | -5 points (max -40) |
| Similar time of day (±2h) | +10 points |

### Key Design Decisions

1. **Idempotency**: Client-provided `Idempotency-Key` header ensures safe retries. See [ADR-001](docs/adr/001-idempotency.md).

2. **Optimistic Locking**: `@Version` field with `If-Match` header support prevents lost updates from concurrent requests.

3. **On-demand Options**: Rebooking options are computed from live flight inventory, not pre-cached, ensuring accuracy.

4. **Stateless Validation**: Selected flight must be in computed options at rebook time, preventing stale selections.

## Technology Stack

| Component | Choice |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Testing | JUnit 5 + Testcontainers |
| API Docs | springdoc-openapi |

## Running Tests

```bash
# Requires Docker for Testcontainers
./gradlew test
```

**Docker Desktop (macOS/Windows/Linux):** Works out of the box.

**Colima (macOS alternative):** Create `~/.testcontainers.properties`:
```properties
docker.host=unix:///Users/YOUR_USERNAME/.colima/default/docker.sock
```

## Domain Simplifications

This service intentionally omits:

- Passenger rights calculations (EU261, NZ Consumer Guarantees Act)
- Partner airline rebooking (codeshares, interline)
- Fare difference handling (upgrade fees, refund credits)
- Seat/meal preference preservation
- Multiple passengers per booking
- Flight capacity tracking

These would be essential in production; omitted here to focus on state management and safe writes.

## Security Note

Authentication and authorization intentionally omitted to focus on domain logic and data integrity. In production, booking access would be scoped by customer identity via JWT claims or session-based auth, with role-based access control for operations staff.
