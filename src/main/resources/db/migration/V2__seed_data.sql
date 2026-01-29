-- Seed data for Flight Rebooking Service

-- Flights (available inventory)
-- f1-f4: AKL→WLG routes
-- f5: AKL→CHC route
-- f6-f8: WLG→AKL routes
-- f9-f10: Additional AKL→WLG routes

INSERT INTO flights (id, flight_number, origin, destination, scheduled_departure) VALUES
    ('00000000-0000-0000-0000-000000000001', 'NZ101', 'AKL', 'WLG', '2026-06-15 08:00:00+00'),
    ('00000000-0000-0000-0000-000000000002', 'NZ103', 'AKL', 'WLG', '2026-06-15 14:00:00+00'),
    ('00000000-0000-0000-0000-000000000003', 'NZ105', 'AKL', 'WLG', '2026-06-16 08:00:00+00'),
    ('00000000-0000-0000-0000-000000000004', 'NZ107', 'AKL', 'WLG', '2026-06-16 14:00:00+00'),
    ('00000000-0000-0000-0000-000000000005', 'NZ201', 'AKL', 'CHC', '2026-06-15 10:00:00+00'),
    ('00000000-0000-0000-0000-000000000006', 'NZ301', 'WLG', 'AKL', '2026-06-15 18:00:00+00'),
    ('00000000-0000-0000-0000-000000000007', 'NZ303', 'WLG', 'AKL', '2026-06-16 08:00:00+00'),
    ('00000000-0000-0000-0000-000000000008', 'NZ305', 'WLG', 'AKL', '2026-06-16 12:00:00+00'),
    ('00000000-0000-0000-0000-000000000009', 'NZ401', 'AKL', 'WLG', '2026-06-15 12:00:00+00'),
    ('00000000-0000-0000-0000-000000000010', 'NZ403', 'AKL', 'WLG', '2026-06-15 16:00:00+00');

-- Bookings

-- BK-001: DISRUPTED, original NZ101 (f1) - Scenario 1: Same-day options available
INSERT INTO bookings (id, reference, status, passenger_name, original_flight_id, rebooked_flight_id, version) VALUES
    ('10000000-0000-0000-0000-000000000001', 'BK-001', 'DISRUPTED', 'Alice Johnson', '00000000-0000-0000-0000-000000000001', NULL, 1);

-- BK-002: CONFIRMED, original NZ201 (f5) - Guard test: Not eligible for rebooking
INSERT INTO bookings (id, reference, status, passenger_name, original_flight_id, rebooked_flight_id, version) VALUES
    ('10000000-0000-0000-0000-000000000002', 'BK-002', 'CONFIRMED', 'Bob Smith', '00000000-0000-0000-0000-000000000005', NULL, 1);

-- BK-003: DISRUPTED, original NZ301 (f6) - Scenario 2: No same-day, next-day only
INSERT INTO bookings (id, reference, status, passenger_name, original_flight_id, rebooked_flight_id, version) VALUES
    ('10000000-0000-0000-0000-000000000003', 'BK-003', 'DISRUPTED', 'Carol Davis', '00000000-0000-0000-0000-000000000006', NULL, 1);

-- BK-004: DISRUPTED, original NZ401 (f9) - Scenario 3: Multiple options, scoring comparison
INSERT INTO bookings (id, reference, status, passenger_name, original_flight_id, rebooked_flight_id, version) VALUES
    ('10000000-0000-0000-0000-000000000004', 'BK-004', 'DISRUPTED', 'David Wilson', '00000000-0000-0000-0000-000000000009', NULL, 1);

-- BK-005: REBOOKED, original NZ101→NZ103 - Guard test: Already rebooked
INSERT INTO bookings (id, reference, status, passenger_name, original_flight_id, rebooked_flight_id, version) VALUES
    ('10000000-0000-0000-0000-000000000005', 'BK-005', 'REBOOKED', 'Eve Martinez', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 2);

-- Disruptions

-- BK-001 disruption: Cancellation due to weather
INSERT INTO disruptions (id, booking_id, type, reason_code, reason_description, occurred_at) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'CANCELLATION', 'WX', 'Weather', '2026-06-14 22:00:00+00');

-- BK-003 disruption: Cancellation due to mechanical
INSERT INTO disruptions (id, booking_id, type, reason_code, reason_description, occurred_at) VALUES
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'CANCELLATION', 'MX', 'Mechanical', '2026-06-15 16:00:00+00');

-- BK-004 disruption: Cancellation due to weather
INSERT INTO disruptions (id, booking_id, type, reason_code, reason_description, occurred_at) VALUES
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'CANCELLATION', 'WX', 'Weather', '2026-06-15 10:00:00+00');

-- BK-005 disruption (historical): Cancellation due to weather
INSERT INTO disruptions (id, booking_id, type, reason_code, reason_description, occurred_at) VALUES
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'CANCELLATION', 'WX', 'Weather', '2026-06-14 22:00:00+00');

-- Rebooking audit for BK-005 (already rebooked)
INSERT INTO rebooking_audit (id, booking_id, idempotency_key, previous_flight_id, new_flight_id, outcome, response_payload) VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'SUCCESS', '{"bookingReference":"BK-005","status":"REBOOKED"}');
