-- TicketSync Sample Data - Events, Zones and Seats
-- Migration: V006__seed_sample_events_zones_seats.sql
-- Purpose: Insert demo events with seating zones and seats for development/testing

-- Date: 2026-05-13

-- ─── Event 1: Concierto de Rock en Vivo ──────────────────────────────────────
WITH ev1 AS (
    INSERT INTO events (name, event_date, venue, description, is_active)
    VALUES (
        'Concierto de Rock en Vivo',
        '2026-07-15 21:00:00',
        'Estadio Nacional',
        'Una noche épica con las mejores bandas de rock.',
        true
    )
    RETURNING event_id
),

-- Zones for Event 1
z1_vip AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'VIP Pista', 150.00 FROM ev1
    RETURNING zone_id
),
z1_general AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'Pista General', 75.00 FROM ev1
    RETURNING zone_id
),
z1_balcon AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'Balcón', 45.00 FROM ev1
    RETURNING zone_id
),

-- Seats: VIP Pista — row A, seats 1-8
s1_vip AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, 'A', n::TEXT, 'AVAILABLE'
    FROM z1_vip, generate_series(1, 8) AS n
    RETURNING seat_id
),

-- Seats: Pista General — rows B-D, seats 1-10
s1_general AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, chr(65 + r)::TEXT, n::TEXT, 'AVAILABLE'
    FROM z1_general, generate_series(1, 3) AS r, generate_series(1, 10) AS n
    RETURNING seat_id
),

-- Seats: Balcón — rows E-G, seats 1-12
s1_balcon AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, chr(68 + r)::TEXT, n::TEXT, 'AVAILABLE'
    FROM z1_balcon, generate_series(1, 3) AS r, generate_series(1, 12) AS n
    RETURNING seat_id
),

-- ─── Event 2: Festival de Jazz ────────────────────────────────────────────────
ev2 AS (
    INSERT INTO events (name, event_date, venue, description, is_active)
    VALUES (
        'Festival de Jazz',
        '2026-08-22 20:00:00',
        'Teatro Municipal',
        'Una velada íntima con los mejores exponentes del jazz contemporáneo.',
        true
    )
    RETURNING event_id
),

-- Zones for Event 2
z2_vip AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'VIP', 120.00 FROM ev2
    RETURNING zone_id
),
z2_general AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'General', 60.00 FROM ev2
    RETURNING zone_id
),
z2_galeria AS (
    INSERT INTO zones (event_id, name, price)
    SELECT event_id, 'Galería', 35.00 FROM ev2
    RETURNING zone_id
),

-- Seats: VIP — row A, seats 1-6
s2_vip AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, 'A', n::TEXT, 'AVAILABLE'
    FROM z2_vip, generate_series(1, 6) AS n
    RETURNING seat_id
),

-- Seats: General — rows B-C, seats 1-10
s2_general AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, chr(65 + r)::TEXT, n::TEXT, 'AVAILABLE'
    FROM z2_general, generate_series(1, 2) AS r, generate_series(1, 10) AS n
    RETURNING seat_id
),

-- Seats: Galería — rows D-E, seats 1-8
s2_galeria AS (
    INSERT INTO seats (zone_id, row_number, seat_number, status)
    SELECT zone_id, chr(67 + r)::TEXT, n::TEXT, 'AVAILABLE'
    FROM z2_galeria, generate_series(1, 2) AS r, generate_series(1, 8) AS n
    RETURNING seat_id
)

-- Final SELECT required to close the CTE chain
SELECT 'Sample data inserted successfully' AS result;
