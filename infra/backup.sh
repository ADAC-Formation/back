#!/usr/bin/env bash
#
# TICKET-040 — Daily PostgreSQL backup to Supabase Storage, with retention purge.
#
# Runs on the VPS (not in a container — needs the Docker socket to reach `db`, which no longer
# publishes its port on the host since TICKET-039). Reads DB_*/SUPABASE_* from the same `.env`
# docker-compose.yml uses, so there is exactly one place these credentials live.
#
# Prerequisites on the VPS: docker (with the compose plugin), curl, jq, gzip, flock.
#
# Cron (run as the same user that owns the docker-compose deployment; adjust paths). `flock -n`
# prevents a second run from starting if a previous one is still hung (e.g. a stalled upload) —
# without it, cron would happily start a second full `pg_dump` on top of the first. Only stdout
# goes to the log; stderr is left alone so cron's own mail-on-stderr behavior (or MAILTO=, if set
# in the crontab) still fires on failure — redirecting both into a log file nobody reads is how
# backup failures go unnoticed for months (branch-wide review):
#
#   0 3 * * * COMPOSE_PROJECT_DIR=/opt/adac-portail flock -n /var/lock/adac-backup.lock \
#     /opt/adac-portail/infra/backup.sh >> /var/log/adac-backup.log
#
# See docs/RESTORE.md for the restoration procedure — this script is only half the story; a
# backup that has never been restored is not a backup, per this ticket's own description.

set -euo pipefail

# --- Configuration -------------------------------------------------------------------------

COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="${ENV_FILE:-${COMPOSE_PROJECT_DIR}/.env}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_NAME="${DB_NAME:-adac_portail}"
SUPABASE_BACKUP_BUCKET="${SUPABASE_BACKUP_BUCKET:-adac-backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
# Matches exactly the names this script itself produces (see DUMP_NAME below) — the purge step
# only ever considers objects matching this pattern, regardless of what else might exist in the
# bucket (branch-wide review, BLOCKING: see the bucket-equality guard further down for why).
DUMP_NAME_PATTERN='^adac_portail_[0-9]{8}T[0-9]{6}Z\.sql\.gz$'

# Read only the specific keys needed from .env, without executing it as shell (branch-wide
# review, BLOCKING): `source "$ENV_FILE"` would run command substitutions/expand `$(...)` a
# secret might contain, silently diverging from how docker-compose itself reads the identical
# file (plain KEY=VALUE, no shell semantics) — and hands code execution to anyone who can write
# to .env. Last matching line wins, matching how repeated keys behave in practice; a wrapping
# pair of quotes is stripped since Compose's own .env parser does the same.
env_value() {
    local key="$1"
    [[ -f "$ENV_FILE" ]] || return 0
    sed -n "s/^${key}=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}

DB_USERNAME="${DB_USERNAME:-$(env_value DB_USERNAME)}"
SUPABASE_URL="${SUPABASE_URL:-$(env_value SUPABASE_URL)}"
SUPABASE_KEY="${SUPABASE_KEY:-$(env_value SUPABASE_KEY)}"
SUPABASE_DOCUMENTS_BUCKET="${SUPABASE_BUCKET:-$(env_value SUPABASE_BUCKET)}"

: "${DB_USERNAME:?DB_USERNAME must be set (via .env or the environment)}"
: "${SUPABASE_URL:?SUPABASE_URL must be set}"
: "${SUPABASE_KEY:?SUPABASE_KEY must be set - the same service-role key the backend app uses}"

# The purge step below deletes anything matching DUMP_NAME_PATTERN older than RETENTION_DAYS,
# unattended, every night. If SUPABASE_BACKUP_BUCKET were ever misconfigured to the documents
# bucket, that purge would start destroying trainee documents instead of old backups — this
# guard makes that specific misconfiguration fail loudly instead (branch-wide review, BLOCKING).
if [[ -n "$SUPABASE_DOCUMENTS_BUCKET" && "$SUPABASE_BACKUP_BUCKET" == "$SUPABASE_DOCUMENTS_BUCKET" ]]; then
    echo "backup.sh: SUPABASE_BACKUP_BUCKET must not be the documents bucket (${SUPABASE_DOCUMENTS_BUCKET})" >&2
    exit 1
fi

cd "$COMPOSE_PROJECT_DIR"

# --- Temp files & secrets ------------------------------------------------------------------
# The service-role key goes in a curl config file (mode 600), never as a `curl -H`/`-d` argv
# value (branch-wide review, BLOCKING): argv is visible to any local account via
# /proc/<pid>/cmdline, and this key bypasses RLS on the whole Supabase project. All temp files
# use mktemp (unique, mode 600, no fixed/predictable name for another local user to pre-place a
# symlink at) and are cleaned up on EXIT/INT/TERM alike.

CURL_CONFIG="$(mktemp -t adac-backup-curlrc-XXXXXX)"
chmod 600 "$CURL_CONFIG"
printf 'header = "Authorization: Bearer %s"\nheader = "apikey: %s"\n' "$SUPABASE_KEY" "$SUPABASE_KEY" > "$CURL_CONFIG"

DUMP_PATH="$(mktemp -t adac-backup-dump-XXXXXX.sql.gz)"
UPLOAD_RESPONSE="$(mktemp -t adac-backup-upload-XXXXXX)"
LIST_RESPONSE="$(mktemp -t adac-backup-list-XXXXXX)"
DELETE_RESPONSE="$(mktemp -t adac-backup-delete-XXXXXX)"
trap 'rm -f "$CURL_CONFIG" "$DUMP_PATH" "$UPLOAD_RESPONSE" "$LIST_RESPONSE" "$DELETE_RESPONSE"' EXIT INT TERM

# --connect-timeout/--max-time on every call (branch-wide review): without them, a stalled
# connection at 3am hangs until the next cron tick — combined with the `flock` in the crontab
# above, a hung run now fails and unlocks instead of silently blocking every future run.
CURL_UPLOAD=(curl -sS -K "$CURL_CONFIG" --connect-timeout 15 --max-time 900)
CURL_API=(curl -sS -K "$CURL_CONFIG" --connect-timeout 15 --max-time 60)

# --- 1. Dump ---------------------------------------------------------------------------------
# Via `docker compose exec`, not a direct `pg_dump -h`: TICKET-039 removed db's public port
# mapping, so the only way in from the host is through the Docker network the container is
# already on. `-T` disables the pseudo-TTY compose would otherwise allocate, which would
# corrupt the piped binary/text stream. `pipefail` (set above) makes a pg_dump failure fail this
# whole line, not just gzip's.

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_NAME="adac_portail_${TIMESTAMP}.sql.gz"

docker compose exec -T "$DB_SERVICE" pg_dump -U "$DB_USERNAME" -d "$DB_NAME" | gzip > "$DUMP_PATH"

if [[ ! -s "$DUMP_PATH" ]]; then
    echo "backup.sh: dump is empty, aborting before upload" >&2
    exit 1
fi
if ! gzip -t "$DUMP_PATH" 2>/dev/null; then
    echo "backup.sh: dump failed gzip integrity check, aborting before upload" >&2
    exit 1
fi

# --- 2. Upload to Supabase Storage ------------------------------------------------------------
# TODO (disclosed, not implemented here): the dump is uploaded as plain gzip, not encrypted.
# INFRASTRUCTURE.md's "encrypted at rest via Supabase" only covers disk encryption, not access
# control — this bucket MUST be created private (no public read policy); see docs/RESTORE.md.
# Client-side encryption (e.g. `gzip | age -r <recipient> `) would close that gap further but
# needs key-management this ticket doesn't scope; flagged for a follow-up ticket.

upload_status="$("${CURL_UPLOAD[@]}" -o "$UPLOAD_RESPONSE" -w '%{http_code}' \
    -X POST "${SUPABASE_URL}/storage/v1/object/${SUPABASE_BACKUP_BUCKET}/${DUMP_NAME}" \
    -H "Content-Type: application/gzip" \
    --data-binary "@${DUMP_PATH}")"

if [[ "$upload_status" -lt 200 || "$upload_status" -ge 300 ]]; then
    echo "backup.sh: upload failed (HTTP ${upload_status})" >&2
    cat "$UPLOAD_RESPONSE" >&2 || true
    exit 1
fi

echo "backup.sh: uploaded ${DUMP_NAME} to bucket ${SUPABASE_BACKUP_BUCKET}"

# --- 3. Purge dumps older than RETENTION_DAYS -------------------------------------------------
# Supabase Storage has no native TTL/lifecycle rule (free tier) — list, filter, then bulk-delete.
# Listing is POST, not GET, per the Storage API. Every step below checks its HTTP status
# explicitly (branch-wide review, CRITICAL): a silently-failed list or delete previously kept
# printing "purged N" while retention had actually stopped working.

list_status="$("${CURL_API[@]}" -o "$LIST_RESPONSE" -w '%{http_code}' \
    -X POST "${SUPABASE_URL}/storage/v1/object/list/${SUPABASE_BACKUP_BUCKET}" \
    -H "Content-Type: application/json" \
    --data '{"limit": 1000, "prefix": ""}')"

if [[ "$list_status" -lt 200 || "$list_status" -ge 300 ]]; then
    echo "backup.sh: listing bucket for purge failed (HTTP ${list_status}) - skipping purge this run" >&2
    cat "$LIST_RESPONSE" >&2 || true
    exit 2
fi

cutoff_epoch="$(date -u -d "-${RETENTION_DAYS} days" +%s 2>/dev/null || date -u -v-"${RETENTION_DAYS}"d +%s)"

# Single jq pass straight into a JSON array (not `jq -R | jq -s`, branch-wide review): avoids
# `-R`'s line-oriented round trip mis-splitting an object name that happened to contain a
# newline, and the array is built by jq itself, so a name containing quotes/backslashes can't
# break the JSON passed to the DELETE call below. The name-pattern filter is the same
# never-touch-anything-else guard as the bucket-equality check above, applied per-object.
old_names_json="$(jq -c --argjson cutoff "$cutoff_epoch" --arg pattern "$DUMP_NAME_PATTERN" '
    [ .[] | select(.name | test($pattern)) |
      select(.created_at != null) |
      select((.created_at | sub("\\.[0-9]+Z$"; "Z") | strptime("%Y-%m-%dT%H:%M:%SZ") | mktime) < $cutoff) |
      .name ]
' "$LIST_RESPONSE")"

old_count="$(echo "$old_names_json" | jq 'length')"

if [[ "$old_count" -gt 0 ]]; then
    delete_status="$("${CURL_API[@]}" -o "$DELETE_RESPONSE" -w '%{http_code}' -X DELETE \
        "${SUPABASE_URL}/storage/v1/object/${SUPABASE_BACKUP_BUCKET}" \
        -H "Content-Type: application/json" \
        --data "{\"prefixes\": ${old_names_json}}")"

    if [[ "$delete_status" -lt 200 || "$delete_status" -ge 300 ]]; then
        # Distinct exit code (branch-wide review): monitoring can tell "no backup taken tonight"
        # (exit 1, above) apart from "backup fine, cleanup failed" (exit 2, here) — the dump
        # itself already succeeded and was not lost.
        echo "backup.sh: purge failed (HTTP ${delete_status}) - dump was uploaded OK" >&2
        cat "$DELETE_RESPONSE" >&2 || true
        exit 2
    fi
    echo "backup.sh: purged ${old_count} dump(s) older than ${RETENTION_DAYS} days"
else
    echo "backup.sh: nothing to purge"
fi
