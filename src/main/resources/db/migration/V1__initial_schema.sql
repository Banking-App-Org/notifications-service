-- Database initialization script for Notifications Service
-- Run this script to set up the required tables

-- Create schema
CREATE SCHEMA IF NOT EXISTS public;

-- Drop existing tables if they exist (for development only)
DROP TABLE IF EXISTS notification_events CASCADE;
DROP TABLE IF EXISTS notification_preferences CASCADE;

-- Notification Events table - Event Sourcing/Audit Trail
CREATE TABLE notification_events (
    id BIGSERIAL PRIMARY KEY,
    member_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    metadata TEXT
);

-- Create indexes for notification_events
CREATE INDEX idx_member_id ON notification_events(member_id);
CREATE INDEX idx_created_at ON notification_events(created_at);
CREATE INDEX idx_event_type ON notification_events(event_type);
CREATE INDEX idx_status ON notification_events(status);

-- Notification Preferences table
CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    member_id VARCHAR(255) NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    notification_frequency VARCHAR(50),
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    opt_in_marketing BOOLEAN NOT NULL DEFAULT true,
    opt_in_updates BOOLEAN NOT NULL DEFAULT true,
    opt_in_promotions BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes for notification_preferences
CREATE INDEX idx_pref_member_id ON notification_preferences(member_id);

-- Comments for documentation
COMMENT ON TABLE notification_events IS 'Immutable event log for audit trail and event sourcing. Stores all notification creation and delivery events.';
COMMENT ON TABLE notification_preferences IS 'User preferences for notification channels, frequency, and opt-in/out settings.';
COMMENT ON COLUMN notification_events.status IS 'Status: CREATED, DELIVERED, FAILED, RETRYING';
COMMENT ON COLUMN notification_events.metadata IS 'Additional JSON payload for context or debugging';

