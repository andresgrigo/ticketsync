-- TicketSync Database Schema - Sales and Audit Tables
-- Migration: V004__create_sales_audit_tables.sql
-- Purpose: Create sales, sale_items, and audit_log tables for transaction recording
-- Date: 2026-04-09

-- sales table: records completed ticket purchase transactions
CREATE TABLE sales (
    sale_id        SERIAL        PRIMARY KEY,
    event_id       INTEGER       NOT NULL REFERENCES events(event_id),
    vendor_id      INTEGER       NOT NULL REFERENCES users(user_id),
    total_amount   DECIMAL(10,2) NOT NULL,
    sale_timestamp TIMESTAMP     NOT NULL DEFAULT NOW(),
    booth_id       VARCHAR(20),
    CONSTRAINT sales_amount_positive CHECK (total_amount > 0)
);

-- sale_items table: junction table linking each sale to its individual purchased seats
CREATE TABLE sale_items (
    sale_item_id SERIAL        PRIMARY KEY,
    sale_id      INTEGER       NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE,
    seat_id      INTEGER       NOT NULL REFERENCES seats(seat_id),
    price_paid   DECIMAL(10,2) NOT NULL,
    CONSTRAINT sale_items_price_positive CHECK (price_paid > 0),
    CONSTRAINT sale_items_seat_unique    UNIQUE (seat_id)
);

-- audit_log table: tracks all business events for operational transparency
CREATE TABLE audit_log (
    log_id      SERIAL       PRIMARY KEY,
    timestamp   TIMESTAMP    NOT NULL DEFAULT NOW(),
    username    VARCHAR(50)  NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50),
    entity_id   INTEGER,
    details     JSONB,
    ip_address  INET,
    session_id  VARCHAR(100)
);

-- Add deferred FK from seats.sale_id to sales(sale_id) — column created in V003
ALTER TABLE seats
    ADD CONSTRAINT seats_sale_id_fk FOREIGN KEY (sale_id) REFERENCES sales(sale_id) ON DELETE SET NULL;

-- Indexes for sales reporting queries
CREATE INDEX idx_sales_event_id       ON sales(event_id);
CREATE INDEX idx_sales_vendor_id      ON sales(vendor_id);
CREATE INDEX idx_sales_sale_timestamp ON sales(sale_timestamp);

-- Index for audit queries by time and action type
CREATE INDEX idx_audit_timestamp_action ON audit_log(timestamp DESC, action);
