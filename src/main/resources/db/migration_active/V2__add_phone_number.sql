-- Add phone_number column to notification_preferences for SMS delivery
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

