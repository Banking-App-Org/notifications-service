ALTER TABLE notification_events ADD COLUMN resent BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_notification_events_resent ON notification_events(resent);
