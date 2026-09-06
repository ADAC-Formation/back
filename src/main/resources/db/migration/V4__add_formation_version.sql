-- TICKET-022 review: optimistic locking on formations, see Formation.version's Javadoc.
ALTER TABLE formations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
