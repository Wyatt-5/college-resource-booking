#!/bin/sh
set -eu
root=${BOOKING_ROOT:-/opt/college-booking}
release=${1:?Usage: activate.sh RELEASE_ID}
case "$release" in ''|*[!A-Za-z0-9._-]*) echo 'Invalid release ID' >&2; exit 1;; esac
test -f "$root/releases/$release/compose.yaml"
docker image inspect "college-booking-backend:$release" >/dev/null
docker image inspect "college-booking-web:$release" >/dev/null
previous=''
if test -L "$root/current"; then
  previous=$(basename "$(readlink -f "$root/current")")
  "$root/current/backup.sh"
fi
if ! BOOKING_RELEASE="$release" "$root/releases/$release/compose.sh" up -d --wait --wait-timeout 240; then
  echo 'Activation failed; showing recent logs' >&2
  BOOKING_RELEASE="$release" "$root/releases/$release/compose.sh" logs --tail 60 >&2
  if test -n "$previous"; then
    BOOKING_RELEASE="$previous" "$root/releases/$previous/compose.sh" up -d --wait --wait-timeout 240
  fi
  exit 1
fi
ln -sfn "$root/releases/$release" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
printf 'Activated %s\n' "$release"
