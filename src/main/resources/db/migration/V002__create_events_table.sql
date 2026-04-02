-- TicketSync Database Schema - Events Table
-- Migration: V002__create_events_table.sql
-- Purpose: Create events table for event lifecycle management (FR06-FR10)

-- Date: 2026-04-02

CREATE TABLE events (
    event_id    SERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    event_date  TIMESTAMP    NOT NULL,
    venue       VARCHAR(200),
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT false,
    created_by  INTEGER      REFERENCES users(user_id),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Index for date-range queries (event listing ordered by date)
CREATE INDEX idx_events_event_date ON events(event_date);

-- Index for is_active filter (vendor POS loads only active events)
CREATE INDEX idx_events_is_active ON events(is_active);
