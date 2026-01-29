# flight-rebooking-service

## Purpose

A portfolio backend service demonstrating production-aware Java/Spring Boot engineering.

**This is not** a full airline system or customer-facing product — it intentionally models a narrow, high-risk workflow to demonstrate backend decision-making and data integrity.

**Target reviewer experience:** Understand design in 2 minutes, run locally in 1 command, see tests pass in CI.

---

## Technology Stack

| Component | Choice |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 (Docker) |
| Migrations | Flyway (no Hibernate auto-ddl) |
| Build | Gradle (Kotlin DSL) |
| Testing | JUnit 5 + Testcontainers |
| API Docs | springdoc-openapi (Swagger UI) |
| CI | GitHub Actions |

---

## API Specification

Base path: `/api/v1`

### Endpoints

```
GET  /api/v1/bookings/{ref}
GET  /api/v1/bookings/{ref}/rebooking-options
POST /api/v1/bookings/{ref}/rebook
GET  /actuator/health
```

---

### GET /api/v1/bookings/{ref}

Returns booking details including current status, original flight, and disruption info if applicable.

**Path parameters:**
- `ref`: Booking reference. Pattern: `^[A-Z0-9-]{3,20}$`

**Response headers:**
- `ETag`: Version number for optimistic concurrency (e.g., `"1"`)

**Response 200:**
```json
{
  "reference": "BK-001",
  "status": "DISRUPTED",
  "passengerName": "Test Passenger",
  "originalFlight": {
    "flightId": "NZ101",
    "flightNumber": "NZ101",
    "origin": "AKL",
    "destination": "WLG",
    "scheduledDeparture": "2026-06-15T08:00:00Z"
  },
  "disruption": {
    "type": "CANCELLATION",
    "reasonCode": "WX",
    "reasonDescription": "Weather",
    "occurredAt": "2026-06-14T22:00:00Z"
  },
  "rebookedFlight": null,
  "version": 1
}
```

**Response 400 (invalid ref format):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Booking reference must be 3-20 alphanumeric characters or hyphens"
}
```

**Response 404:**
```json
{
  "type": "about:blank",
  "title": "Booking not found",
  "status": 404,
  "detail": "No booking found with reference BK-999"
}
```

---

### GET /api/v1/bookings/{ref}/rebooking-options

Computes available rebooking options on-demand from current flight inventory. Options are NOT persisted.

**Path parameters:**
- `ref`: Booking reference. Pattern: `^[A-Z0-9-]{3,20}$`

**Response 200:**
```json
{
  "bookingReference": "BK-001",
  "generatedAt": "2026-06-15T09:00:00Z",
  "options": [
    {
      "flight": {
        "flightId": "NZ103",
        "flightNumber": "NZ103",
        "origin": "AKL",
        "destination": "WLG",
        "scheduledDeparture": "2026-06-15T14:00:00Z"
      },
      "score": 92,
      "reason": "Same day, 6h later than original, direct flight"
    },
    {
      "flight": {
        "flightId": "NZ105",
        "flightNumber": "NZ105",
        "origin": "AKL",
        "destination": "WLG",
        "scheduledDeparture": "2026-06-16T08:00:00Z"
      },
      "score": 71,
      "reason": "Next day, same departure time, direct flight"
    }
  ]
}
```

**Response 409 (not eligible):**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking BK-002 is in CONFIRMED state; only DISRUPTED bookings can be rebooked"
}
```

---

### POST /api/v1/bookings/{ref}/rebook

Confirms rebooking to a selected flight. Idempotent via `Idempotency-Key` header.

**Path parameters:**
- `ref`: Booking reference. Pattern: `^[A-Z0-9-]{3,20}$`

**Request headers:**
- `Idempotency-Key`: Required. UUID format. Example: `550e8400-e29b-41d4-a716-446655440000`
- `If-Match`: Optional. ETag value for explicit optimistic concurrency.
- `Content-Type`: `application/json`

**Request body:**
```json
{
  "selectedFlightId": "NZ103"
}
```

**Response 201 (success — first execution):**
```json
{
  "bookingReference": "BK-001",
  "status": "REBOOKED",
  "previousFlight": {
    "flightId": "NZ101",
    "flightNumber": "NZ101",
    "scheduledDeparture": "2026-06-15T08:00:00Z"
  },
  "newFlight": {
    "flightId": "NZ103",
    "flightNumber": "NZ103",
    "scheduledDeparture": "2026-06-15T14:00:00Z"
  },
  "rebookedAt": "2026-06-15T09:05:00Z"
}
```

**Response 200 (idempotent replay — same key, already processed):**
Same body as 201. Status 200 indicates stored outcome returned, not re-executed.

**Response 400 (missing/invalid idempotency key):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Idempotency-Key header is required and must be a valid UUID"
}
```

**Response 400 (invalid request body):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "selectedFlightId is required"
}
```

**Response 400 (invalid flight selection):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Flight NZ999 is not a valid rebooking option for this booking"
}
```

**Response 409 (booking not in DISRUPTED state):**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking BK-001 is in CONFIRMED state; only DISRUPTED bookings can be rebooked"
}
```

**Response 409 (already rebooked with different key):**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking BK-001 has already been rebooked"
}
```

**Response 409 (optimistic lock conflict):**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking was modified by another request; please retry"
}
```

---

## Data Models

### Booking

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| reference | String | Unique, indexed. Pattern: `^[A-Z0-9-]{3,20}$` |
| status | Enum | CONFIRMED, DISRUPTED, REBOOKED, CANCELLED |
| passengerName | String | |
| originalFlightId | UUID | FK to Flight |
| rebookedFlightId | UUID | FK to Flight, nullable |
| version | Long | `@Version` for optimistic locking |
| createdAt | Timestamp | |
| updatedAt | Timestamp | |

### Flight

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| flightNumber | String | e.g., "NZ101" |
| origin | String | 3-letter IATA code |
| destination | String | 3-letter IATA code |
| scheduledDeparture | Timestamp | |
| createdAt | Timestamp | |

### Disruption

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| bookingId | UUID | FK to Booking |
| type | Enum | CANCELLATION, DELAY, SCHEDULE_CHANGE |
| reasonCode | String | WX, MX, CR, ATC, SC |
| reasonDescription | String | Weather, Mechanical, Crew, Air Traffic Control, Schedule Change |
| occurredAt | Timestamp | |
| createdAt | Timestamp | |

### RebookingAudit

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| bookingId | UUID | FK to Booking |
| idempotencyKey | UUID | Indexed |
| previousFlightId | UUID | FK to Flight |
| newFlightId | UUID | FK to Flight |
| outcome | Enum | SUCCESS, CONFLICT, ERROR |
| responsePayload | JSONB | Stored response for idempotent replay |
| createdAt | Timestamp | |

---

## State Machine

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
    [rebook success]                  [cancel - Phase 2]
         │                                  │
         ▼                                  ▼
  ┌──────────────┐                   ┌──────────────┐
  │   REBOOKED   │                   │  CANCELLED   │
  │  (terminal)  │                   │  (terminal)  │
  └──────────────┘                   └──────────────┘
```

### Guarded Transitions

| Current State | Action | Result |
|---------------|--------|--------|
| CONFIRMED | GET /rebooking-options | 409 Conflict |
| CONFIRMED | POST /rebook | 409 Conflict |
| DISRUPTED | GET /rebooking-options | 200 — computed options |
| DISRUPTED | POST /rebook (valid) | 201 — transitions to REBOOKED |
| DISRUPTED | POST /rebook (same idempotency key) | 200 — returns stored outcome |
| DISRUPTED | POST /rebook (different key after success) | 409 Conflict |
| REBOOKED | GET /rebooking-options | 409 Conflict |
| REBOOKED | POST /rebook | 409 Conflict |
| CANCELLED | Any rebooking action | 409 Conflict |

---

## Scoring Algorithm

Options are computed on-demand. Only flights matching the original route (origin → destination) are considered.

```
SCORING ALGORITHM

For each available flight on the same route after the disruption:

  base_score = 100

  # Prefer same-day (30 point penalty for different day)
  if flight.date != original.date:
    base_score -= 30

  # Penalise delay (5 points per hour, capped at 40)
  delay_hours = (flight.departure - original.departure) / 60
  delay_penalty = min(delay_hours * 5, 40)
  base_score -= delay_penalty

  # Bonus for similar departure time (within 2 hours of original time-of-day)
  time_diff_minutes = abs(flight.time_of_day - original.time_of_day)
  if time_diff_minutes <= 120:
    base_score += 10

  final_score = max(base_score, 0)

Sort by score descending.
Tie-break: earliest departure wins.

Return top 5 options.
```

### Reason String Templates

- Same day, minimal delay: `"Same day, {hours}h later than original, direct flight"`
- Same day, similar time: `"Same day, similar departure time, direct flight"`
- Next day: `"Next day, {time} departure, direct flight"`
- Significant delay: `"{days} days later, {time} departure"`

---

## Seed Data

Preloaded via Flyway migration `V2__seed_data.sql`.

### Flights (available inventory)

| Flight ID | Number | Route | Departure |
|-----------|--------|-------|-----------|
| f1 | NZ101 | AKL→WLG | 2026-06-15 08:00 |
| f2 | NZ103 | AKL→WLG | 2026-06-15 14:00 |
| f3 | NZ105 | AKL→WLG | 2026-06-16 08:00 |
| f4 | NZ107 | AKL→WLG | 2026-06-16 14:00 |
| f5 | NZ201 | AKL→CHC | 2026-06-15 10:00 |
| f6 | NZ301 | WLG→AKL | 2026-06-15 18:00 |
| f7 | NZ303 | WLG→AKL | 2026-06-16 08:00 |
| f8 | NZ305 | WLG→AKL | 2026-06-16 12:00 |
| f9 | NZ401 | AKL→WLG | 2026-06-15 12:00 |
| f10 | NZ403 | AKL→WLG | 2026-06-15 16:00 |

### Bookings

| Reference | Status | Original Flight | Disruption | Test Scenario |
|-----------|--------|-----------------|------------|---------------|
| BK-001 | DISRUPTED | NZ101 (f1) | CANCELLATION (WX) | Scenario 1: Same-day options available |
| BK-002 | CONFIRMED | NZ201 (f5) | None | Guard test: Not eligible for rebooking |
| BK-003 | DISRUPTED | NZ301 (f6) | CANCELLATION (MX) | Scenario 2: No same-day, next-day only |
| BK-004 | DISRUPTED | NZ401 (f9) | CANCELLATION (WX) | Scenario 3: Multiple options, scoring comparison |
| BK-005 | REBOOKED | NZ101→NZ103 | CANCELLATION (WX) | Guard test: Already rebooked |

---

## Test Scenarios (Must Pass)

### Scenario 1: Same-day rebooking
- BK-001 is DISRUPTED (original: NZ101 08:00)
- GET /rebooking-options returns NZ103 (14:00) scored highest (same day)
- POST /rebook with NZ103 succeeds → status REBOOKED

### Scenario 2: Next-day only
- BK-003 is DISRUPTED (original: NZ301 WLG→AKL 18:00)
- No same-day WLG→AKL flights available after 18:00
- GET /rebooking-options returns NZ303, NZ305 (next day)

### Scenario 3: Scoring comparison
- BK-004 is DISRUPTED (original: NZ401 12:00)
- Multiple AKL→WLG options: NZ103 (14:00), NZ105 (next day 08:00), NZ107 (next day 14:00)
- Verify scoring: NZ103 > NZ105 > NZ107

### Scenario 4: Idempotency and conflict
- Rebook BK-001 with key A → 201 Success
- Rebook BK-001 with key A again → 200 (same response)
- Rebook BK-001 with key B → 409 Conflict (already rebooked)

### Scenario 5: State guards
- BK-002 (CONFIRMED): GET /rebooking-options → 409
- BK-002 (CONFIRMED): POST /rebook → 409
- BK-005 (REBOOKED): POST /rebook → 409

### Scenario 6: Optimistic locking
- Load BK-001 version 1
- Modify booking externally (simulate concurrent update)
- POST /rebook with stale version → 409 "modified by another request"

---

## Implementation Phases

### Phase 0: Spike (Optional, Throwaway)
If unfamiliar with Spring Boot, create a separate throwaway repo:
- Spring Boot + Postgres + Flyway
- One table, one endpoint
- Verify local Docker Compose works
- Delete after learning

### Phase 1: Project Skeleton
**Goal:** Empty runnable app with infrastructure.

1. Initialize Spring Boot project (start.spring.io or Gradle init)
   - Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway, Actuator, Validation, springdoc-openapi
2. Create `docker-compose.yml` with Postgres
3. Create `application.yml` with datasource config
4. Create empty Flyway migration `V1__initial_schema.sql`
5. Verify `./gradlew bootRun` starts and `/actuator/health` returns UP

**Test checkpoint:** App starts, connects to Postgres, health endpoint works.

### Phase 2: Schema and Seed Data
**Goal:** Database populated, no API yet.

1. Write `V1__initial_schema.sql`:
   - flights table
   - bookings table with status enum
   - disruptions table
   - rebooking_audit table
2. Write `V2__seed_data.sql` with all test fixtures
3. Verify tables created and data loaded via psql

**Test checkpoint:** `SELECT * FROM bookings` shows 5 rows with correct statuses.

### Phase 3: GET /bookings/{ref}
**Goal:** First read endpoint working.

1. Create JPA entities: Flight, Booking, Disruption
2. Create BookingRepository (Spring Data JPA)
3. Create BookingController with GET endpoint
4. Create BookingResponse DTO
5. Add ETag header from version field
6. Add input validation (booking ref pattern)
7. Add Problem+JSON error responses

**Test checkpoint:**
- Unit test: Controller returns correct DTO
- Integration test (Testcontainers): GET BK-001 returns DISRUPTED with disruption details
- Integration test: GET BK-999 returns 404
- Integration test: GET invalid-ref! returns 400

### Phase 4: Rebooking Options (Scoring Engine)
**Goal:** Core domain logic implemented.

1. Create RebookingService with scoring algorithm
2. Create RebookingOption DTO
3. Add GET /rebooking-options endpoint
4. Generate reason strings

**Test checkpoint:**
- Unit tests for scoring algorithm (isolated, no DB):
  - Same-day flight scores higher than next-day
  - Delay penalty calculated correctly
  - Similar time bonus applied
  - Tie-break by departure time
- Integration test: BK-001 options returns NZ103 first
- Integration test: BK-002 (CONFIRMED) returns 409
- Integration test: BK-003 returns only next-day flights

### Phase 5: POST /rebook (Happy Path)
**Goal:** State transition works.

1. Create RebookRequest DTO with validation
2. Add POST endpoint
3. Implement state transition DISRUPTED → REBOOKED
4. Validate selected flight is in computed options
5. Save rebooking audit record

**Test checkpoint:**
- Integration test: Rebook BK-001 to NZ103 → 201, status REBOOKED
- Integration test: GET BK-001 after rebook shows rebookedFlight populated
- Verify audit table has record

### Phase 6: Idempotency
**Goal:** Safe retries.

1. Extract and validate Idempotency-Key header (UUID format)
2. Check audit table for existing key before processing
3. If found with same booking, return stored response with 200
4. If found with different booking, return 400 (key reuse)

**Test checkpoint:**
- Integration test: Same key twice → 200 with identical response
- Integration test: Verify audit table has only one row (not duplicated)

### Phase 7: Conflict Handling
**Goal:** Optimistic locking and state guards complete.

1. Handle OptimisticLockException → 409
2. Guard against rebooking already-REBOOKED booking with different key → 409
3. Implement If-Match header check (optional ETag validation)

**Test checkpoint:**
- Integration test: Rebook with different key after success → 409
- Unit test: Simulate version conflict → 409
- Integration test: BK-005 (already REBOOKED) → 409

### Phase 8: Polish and Documentation
**Goal:** Reviewer-ready.

1. Enable springdoc-openapi, verify Swagger UI at /swagger-ui.html
2. Write README.md:
   - 1-command run instructions
   - 5 curl examples covering happy path and errors
   - State diagram
   - Design decisions summary
   - Security note (auth intentionally omitted)
   - Domain simplifications note
3. Write `docs/adr/001-idempotency.md`
4. Configure GitHub Actions CI

**Test checkpoint:**
- Fresh clone → `docker-compose up -d && ./gradlew bootRun` works
- All curl examples in README work
- CI passes

---

## Definition of Done

The project is complete when:

- [ ] All 6 test scenarios pass in CI
- [ ] Fresh clone runs with one command
- [ ] README curl commands work against running service
- [ ] Swagger UI accessible at /swagger-ui.html
- [ ] Reviewer can understand design in <3 minutes from README
- [ ] No commented-out code, no TODOs in main branch

---

## Validation Rules Summary

| Input | Rule | Error |
|-------|------|-------|
| `{ref}` path param | `^[A-Z0-9-]{3,20}$` | 400 |
| `Idempotency-Key` header | Required on POST, valid UUID | 400 |
| `selectedFlightId` body | Required, non-empty string | 400 |
| Flight selection | Must be in computed options | 400 |
| Booking state | Must be DISRUPTED for rebooking | 409 |

---

## Deferred to Phase 2

| Feature | Notes |
|---------|-------|
| POST /bookings/{ref}/cancel | State machine supports CANCELLED; needs endpoint |
| Capacity modelling | Currently infinite; would 409 if flight full |
| Idempotency key expiry | Currently unbounded; production: 24h TTL |
| Audit trail API | Currently DB-only; could expose GET /bookings/{ref}/history |
| Correlation ID logging | Request tracing header propagation |
| Rebooking deadline | Time-bounded rebooking window |

---

## README Structure (Write Last)

```markdown
# Flight Rebooking Service

A production-aware backend for airline disruption recovery.

## Quick Start

docker-compose up -d
./gradlew bootRun

## API Examples

[5 curl commands with expected outputs]

## Design

[State diagram]
[Scoring algorithm summary]
[Key decisions: idempotency, optimistic locking]

## Domain Simplifications

This service intentionally omits:
- Passenger rights calculations (EU261, NZ Consumer Guarantees Act)
- Partner airline rebooking (codeshares, interline)
- Fare difference handling (upgrade fees, refund credits)
- Seat/meal preference preservation
- Multiple passengers per booking

These would be essential in production; omitted here to focus on state management and safe writes.

## Security Note

Authentication and authorization intentionally omitted to focus on domain logic and data integrity. In production, booking access would be scoped by customer identity via JWT claims or session-based auth, with role-based access control for operations staff.
```

---

## Notes for Claude Code

### Testing Approach
- Write tests BEFORE or alongside implementation, not after
- Use Testcontainers for integration tests (real Postgres, not H2)
- Unit test the scoring algorithm in isolation (no Spring context needed)
- Name tests descriptively: `shouldReturn409WhenBookingNotDisrupted()`

### Error Handling
- Use `@RestControllerAdvice` for global exception handling
- Return consistent Problem+JSON structure for all errors
- Map specific exceptions to HTTP status codes

### Code Organization
```
src/main/java/com/example/flightrebooking/
├── FlightRebookingApplication.java
├── controller/
│   └── BookingController.java
├── service/
│   ├── BookingService.java
│   └── RebookingService.java (scoring logic)
├── repository/
│   └── BookingRepository.java
├── entity/
│   ├── Booking.java
│   ├── Flight.java
│   ├── Disruption.java
│   └── RebookingAudit.java
├── dto/
│   ├── BookingResponse.java
│   ├── RebookingOptionResponse.java
│   ├── RebookRequest.java
│   └── ProblemDetail.java
├── exception/
│   ├── BookingNotFoundException.java
│   ├── BookingNotEligibleException.java
│   └── GlobalExceptionHandler.java
└── config/
    └── OpenApiConfig.java
```

### Key Spring Annotations
- `@Version` on Booking.version for optimistic locking
- `@Valid` on request bodies
- `@Pattern` for booking reference validation
- `@Transactional` on service methods that write

### Gotchas
- Flyway migrations must be idempotent or versioned correctly
- JPA entity equals/hashCode should use business key (reference), not id
- Testcontainers needs Docker running
- Spring Data JPA returns Optional; handle empty correctly