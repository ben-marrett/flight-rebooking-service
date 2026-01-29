-- Flight Rebooking Service Schema

-- Booking status enum
CREATE TYPE booking_status AS ENUM ('CONFIRMED', 'DISRUPTED', 'REBOOKED', 'CANCELLED');

-- Disruption type enum
CREATE TYPE disruption_type AS ENUM ('CANCELLATION', 'DELAY', 'SCHEDULE_CHANGE');

-- Rebooking audit outcome enum
CREATE TYPE rebooking_outcome AS ENUM ('SUCCESS', 'CONFLICT', 'ERROR');

-- Flights table
CREATE TABLE flights (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    scheduled_departure TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flights_route ON flights (origin, destination);
CREATE INDEX idx_flights_departure ON flights (scheduled_departure);

-- Bookings table
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    reference VARCHAR(20) NOT NULL UNIQUE,
    status booking_status NOT NULL DEFAULT 'CONFIRMED',
    passenger_name VARCHAR(255) NOT NULL,
    original_flight_id UUID NOT NULL REFERENCES flights(id),
    rebooked_flight_id UUID REFERENCES flights(id),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_bookings_reference ON bookings (reference);

-- Disruptions table
CREATE TABLE disruptions (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    type disruption_type NOT NULL,
    reason_code VARCHAR(10) NOT NULL,
    reason_description VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_disruptions_booking ON disruptions (booking_id);

-- Rebooking audit table
CREATE TABLE rebooking_audit (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    idempotency_key UUID NOT NULL,
    previous_flight_id UUID NOT NULL REFERENCES flights(id),
    new_flight_id UUID NOT NULL REFERENCES flights(id),
    outcome rebooking_outcome NOT NULL,
    response_payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rebooking_audit_idempotency ON rebooking_audit (idempotency_key);
CREATE INDEX idx_rebooking_audit_booking ON rebooking_audit (booking_id);
