-- TicketSync Database Schema - Initial Baseline
-- Migration: V001__initial_schema.sql
-- Purpose: Establish schema version tracking

-- Date: 2026-03-31

-- Create schema_version table for application version validation
-- This table allows the application to verify schema compatibility at startup
CREATE TABLE schema_version (
    version VARCHAR(20) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT NOW(),
    description TEXT
);

-- Insert initial version matching application version in pom.xml
INSERT INTO schema_version (version, description) 
VALUES ('1.0-SNAPSHOT', 'Initial schema baseline for TicketSync');

-- Note: Flyway automatically creates and manages the flyway_schema_history table
-- Do NOT create this table manually
