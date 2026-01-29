-- Add unique constraint on idempotency_key to prevent race conditions
-- Two concurrent requests with same key will now fail at DB level
ALTER TABLE rebooking_audit ADD CONSTRAINT uk_rebooking_audit_idempotency_key UNIQUE (idempotency_key);
