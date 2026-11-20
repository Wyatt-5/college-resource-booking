#!/bin/sh
set -eu
umask 077
root=${BOOKING_ROOT:-/opt/college-booking}
cd "$root"
exec 9>"$root/backup.lock"
flock -n 9 || { echo 'Another backup is running' >&2; exit 1; }
stamp=$(date -u +%Y%m%dT%H%M%SZ)
destination="$root/backups/$stamp"
mkdir -p "$destination"
dc="$root/current/compose.sh"
restart_services() { "$dc" start backend web >/dev/null; }
trap restart_services EXIT HUP INT TERM
# Pause all application writes so the database and file snapshot agree.
"$dc" stop web backend >/dev/null
"$dc" exec -T postgres pg_dump -U booking -d college_booking -Fc > "$destination/database.dump"
uploads=$(docker volume inspect college-booking_uploads --format '{{.Mountpoint}}')
test -n "$uploads" && test -d "$uploads"
tar -C "$uploads" -czf "$destination/uploads.tar.gz" .
readlink -f "$root/current" > "$destination/release.txt"
cd "$destination"
sha256sum database.dump uploads.tar.gz release.txt > SHA256SUMS
printf '%s\n' "$destination"
