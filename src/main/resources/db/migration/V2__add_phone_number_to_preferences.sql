-- Add phone number support for SMS notifications
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

