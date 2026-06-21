-- Canonical Flyway baseline for active migrations
CREATE SCHEMA IF NOT EXISTS public;

DROP TABLE IF EXISTS notification_events CASCADE;
DROP TABLE IF EXISTS notification_preferences CASCADE;

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

CREATE INDEX idx_member_id ON notification_events(member_id);
CREATE INDEX idx_created_at ON notification_events(created_at);
CREATE INDEX idx_event_type ON notification_events(event_type);
CREATE INDEX idx_status ON notification_events(status);

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

CREATE INDEX idx_pref_member_id ON notification_preferences(member_id);

