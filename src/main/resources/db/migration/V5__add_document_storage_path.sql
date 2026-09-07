-- TICKET-026: the raw Supabase Storage object path (e.g. "formations/1/<uuid>-programme.pdf"),
-- distinct from documents.file_url (the full authenticated object URL, already percent-encoded
-- for HTTP). StorageServiceImpl needs the un-encoded path back to issue a download request;
-- deriving it from file_url would mean undoing SupabaseConfig's percent-encoding, a lossy round
-- trip for any file name with special characters. Not exposed via DocumentResponse (internal only
-- — see docs/tech.md § DocumentResponse, unchanged by this ticket).
-- Nullable-then-backfill-then-NOT-NULL rather than a plain "ADD COLUMN ... NOT NULL": this repo's
-- dev database is a real persistent Postgres instance, not recreated per run, so any document row
-- from earlier manual testing would make a bare NOT NULL fail this migration outright.
ALTER TABLE documents ADD COLUMN storage_path TEXT;
UPDATE documents SET storage_path = '' WHERE storage_path IS NULL;
ALTER TABLE documents ALTER COLUMN storage_path SET NOT NULL;
