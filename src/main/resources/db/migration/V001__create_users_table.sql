-- TicketSync Database Schema - Users Table
-- Migration: V001__create_users_table.sql
-- Purpose: Create users table for authentication and access control

-- Date: 2026-04-01
--
-- SECURITY NOTE: This migration inserts a default admin user for development convenience.
-- WARNING: The default password MUST be changed immediately after first login in any
-- non-development environment. Never use this default credential in production.
-- Default credentials are documented in the project README (dev environment only).

CREATE TABLE users (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    password_hash VARCHAR(60)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'VENDOR'))
);

-- Default admin seed user (dev environment only)
-- BCrypt cost factor 12. See README for default credentials (dev only).
-- IMPORTANT: Change this password immediately after first login.
INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2a$12$bPjnKcxOg4MA0yr0NraJ5uKNIbDFRcohMdIK/VCYANK3HzkFEwDUW', 'ADMIN');

-- Developer vendor seed (dev-only)
-- Creates a vendor with user_id=2 when run after the admin insert.
INSERT INTO users (username, password_hash, role)
VALUES ('vendor1', '$2a$12$YjOf6Ml0WGulH45l.DRX6uXqty4OHrLVAbDao9F9xG8/olzt1LzAG', 'VENDOR');
