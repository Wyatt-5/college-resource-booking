#!/bin/sh
set -eu
umask 077
root=${BOOKING_ROOT:-/opt/college-booking}
backup=${1:?Usage: verify-backup.sh /opt/college-booking/backups/TIMESTAMP}
backup=$(realpath "$backup")
case "$backup" in "$root"/backups/*) ;; *) echo 'Backup must be under the deployment backup directory' >&2; exit 1;; esac
cd "$backup"
sha256sum -c SHA256SUMS
dc="$root/current/compose.sh"
database="restore_check_$(date -u +%Y%m%d%H%M%S)"
"$dc" exec -T postgres createdb -U booking "$database"
cleanup() { "$dc" exec -T postgres dropdb -U booking "$database" >/dev/null; }
trap cleanup EXIT HUP INT TERM
"$dc" exec -T postgres pg_restore -U booking -d "$database" --no-owner --no-acl --exit-on-error < database.dump
"$dc" exec -T postgres psql -U booking -d "$database" -v ON_ERROR_STOP=1 <<'SQL'
DO $verify$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='16' AND success) THEN
    RAISE EXCEPTION 'Restored database is missing the required V16 migration';
  END IF;
END
$verify$;
SELECT count(*) AS applied_migrations FROM flyway_schema_history WHERE success;
SELECT count(*) AS restored_users FROM users;
SELECT count(*) AS restored_reservations FROM reservations;
SQL
verify_dir=$(mktemp -d "$root/backups/.verify-XXXXXX")
tar -xzf uploads.tar.gz -C "$verify_dir"
tar -dzf uploads.tar.gz -C "$verify_dir"
printf 'Restore verification passed; extracted files retained at %s\n' "$verify_dir"
