-- TicketSync Database Schema - Initial Baseline
-- Migration: V001__initial_schema.sql
-- Purpose: Establish schema version tracking

-- Date: 2026-03-31

-- Create schema_version table for application version validation
-- This table allows the application to verify schema compatibility at startup
CREATE TABLE schema_version (
    version VARCHAR(50) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT NOW(),
    description TEXT
);

-- Insert initial version matching application version in pom.xml
-- Using ON CONFLICT for idempotency in case migration is re-run
INSERT INTO schema_version (version, description) 
VALUES ('1.0-SNAPSHOT', 'Initial schema baseline for TicketSync')
ON CONFLICT (version) DO NOTHING;

-- Note: Flyway automatically creates and manages the flyway_schema_history table.
-- Do NOT manually create the flyway_schema_history table (this warning refers to Flyway's table, not schema_version above).
