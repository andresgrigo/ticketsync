-- TicketSync Database Schema - Zones and Seats Tables
-- Migration: V003__create_zones_seats_tables.sql
-- Purpose: Create zones and seats tables for venue seating layout with zone-based pricing (FR11-FR14)

-- Date: 2026-04-07

-- zones table: each zone belongs to one event and carries a single price tier
CREATE TABLE zones (
    zone_id   SERIAL        PRIMARY KEY,
    event_id  INTEGER       NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    name      VARCHAR(100)  NOT NULL,
    price     DECIMAL(10,2) NOT NULL,
    CONSTRAINT zones_price_positive CHECK (price > 0)
);

-- seats table: each seat belongs to one zone; status lifecycle managed by the application
CREATE TABLE seats (
    seat_id     SERIAL      PRIMARY KEY,
    zone_id     INTEGER     NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    row_number  VARCHAR(10) NOT NULL,
    seat_number VARCHAR(10) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    sale_id     INTEGER     NULL,
    -- sale_id FK to sales table deferred
    CONSTRAINT seats_status_check  CHECK (status IN ('AVAILABLE', 'SOLD', 'RESERVED', 'DISABLED')),
    CONSTRAINT seats_row_nonempty  CHECK (LENGTH(TRIM(row_number)) > 0),
    CONSTRAINT seats_seat_nonempty CHECK (LENGTH(TRIM(seat_number)) > 0)
);

-- Index for fast zone-by-event queries (FK in PostgreSQL does not auto-create index)
CREATE INDEX idx_zones_event_id ON zones(event_id);

-- Index for fast seat-by-zone queries (FK in PostgreSQL does not auto-create index)
CREATE INDEX idx_seats_zone_id ON seats(zone_id);

-- UNIQUE constraint: prevents duplicate zone names within the same event (enforced at DB level)
CREATE UNIQUE INDEX idx_zones_event_name ON zones(event_id, name);

-- UNIQUE composite index: prevents duplicate seat definitions and supports duplicate detection
CREATE UNIQUE INDEX idx_seats_zone_row_seat ON seats(zone_id, row_number, seat_number);

-- Index for seat availability filter queries (vendor POS availability display)
CREATE INDEX idx_seats_status ON seats(status);
