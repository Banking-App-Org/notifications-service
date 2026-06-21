# Notifications Service — ERD

Database schema as of migration `V4__add_resent_column.sql` (active migrations in
`src/main/resources/db/migration_active/`).

There is no physical foreign key between the two tables — they are linked
logically by `member_id`: one `notification_preferences` row per member
(unique constraint), many `notification_events` rows per member.

```mermaid
erDiagram
    NOTIFICATION_PREFERENCES ||..o{ NOTIFICATION_EVENTS : "member_id (logical, no FK)"

    NOTIFICATION_EVENTS {
        BIGSERIAL id PK
        VARCHAR(255) member_id "NOT NULL, indexed"
        VARCHAR(255) title "NOT NULL"
        TEXT description
        VARCHAR(50) event_type "NOT NULL, indexed (registration, enrollment, payment...)"
        VARCHAR(50) status "NOT NULL, indexed (CREATED, DELIVERED, FAILED, RETRYING)"
        TIMESTAMP created_at "NOT NULL, indexed"
        TIMESTAMP delivered_at
        INTEGER retry_count "DEFAULT 0"
        TEXT error_message
        TEXT metadata "JSON payload"
        BOOLEAN resent "NOT NULL, DEFAULT false"
    }

    NOTIFICATION_PREFERENCES {
        BIGSERIAL id PK
        VARCHAR(255) member_id UK "NOT NULL, unique, indexed"
        VARCHAR(100) email
        BOOLEAN email_enabled "NOT NULL, DEFAULT true"
        VARCHAR(20) phone_number
        BOOLEAN sms_enabled "NOT NULL, DEFAULT false"
        BOOLEAN push_enabled "NOT NULL, DEFAULT true"
        VARCHAR(50) notification_frequency "IMMEDIATE, DAILY, WEEKLY, MONTHLY"
        VARCHAR(5) quiet_hours_start "HH:mm"
        VARCHAR(5) quiet_hours_end "HH:mm"
        BOOLEAN opt_in_marketing "NOT NULL, DEFAULT true"
        BOOLEAN opt_in_updates "NOT NULL, DEFAULT true"
        BOOLEAN opt_in_promotions "NOT NULL, DEFAULT false"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }
```

## Notes

- **`notification_events`** is an immutable audit/event-sourcing table written by the
  Kafka consumer pipeline (`NotificationEvent` entity). Indexes on `member_id`,
  `created_at`, `event_type`, and `status`.
- **`notification_preferences`** holds per-member delivery settings (`NotificationPreference`
  entity), one row per member enforced by the unique constraint on `member_id`.
- The relationship is dashed in the diagram because it is application-level only;
  the database defines no foreign key, and events can exist for members with no
  preference row (defaults are applied in code).
