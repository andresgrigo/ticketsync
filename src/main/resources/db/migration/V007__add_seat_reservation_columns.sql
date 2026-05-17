-- V007: Add soft-reservation columns to seats table
--
-- reserved_by   : user ID (as text) of the vendor holding the reservation
-- reserved_until: expiry timestamp; reservation is void once this passes
--
-- An active reservation satisfies: status = 'RESERVED' AND reserved_until > NOW()
-- An expired reservation is treated as AVAILABLE by the application layer.
ALTER TABLE seats
    ADD COLUMN reserved_by    TEXT        NULL,
    ADD COLUMN reserved_until TIMESTAMPTZ NULL;
