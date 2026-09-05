-- TICKET-019 branch-wide review: optimistic locking on users, see User.version's Javadoc.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
