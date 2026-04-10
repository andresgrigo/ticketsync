-- V005__create_seat_notification_trigger.sql
-- Creates PostgreSQL LISTEN/NOTIFY trigger for real-time seat status synchronization.
-- Fires pg_notify('seat_update', seat_id) whenever a seat's status changes.

CREATE FUNCTION notify_seat_change() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('seat_update', NEW.seat_id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER seat_status_notify
    AFTER UPDATE ON seats
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION notify_seat_change();
