#!/bin/sh
set -eu
root=${BOOKING_ROOT:-/opt/college-booking}
release=${BOOKING_RELEASE:-$(basename "$(readlink -f "$root/current")")}
case "$release" in ''|*[!A-Za-z0-9._-]*) echo 'Invalid release ID' >&2; exit 1;; esac
export RELEASE_ID="$release"
exec docker compose --env-file "$root/.env" -f "$root/releases/$release/compose.yaml" "$@"
