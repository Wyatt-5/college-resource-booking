#!/bin/sh
set -eu
umask 077
test "$(id -u)" = 0 || { echo 'Run initial setup as an administrator' >&2; exit 1; }
root=${BOOKING_ROOT:-/opt/college-booking}
release=${1:?Release ID required}
case "$release" in ''|*[!A-Za-z0-9._-]*) echo 'Invalid release ID' >&2; exit 1;; esac
test -f "$root/releases/$release/tunnel.pub"
test -f "$root/.env"
install -d -m 700 "$root/backups" "$root/incoming"
if ! id booking-tunnel >/dev/null 2>&1; then
  useradd --system --create-home --shell /bin/sh booking-tunnel
fi
test "$(getent passwd booking-tunnel | cut -d: -f6)" = /home/booking-tunnel
install -d -m 700 -o booking-tunnel -g booking-tunnel /home/booking-tunnel/.ssh
key=$(cat "$root/releases/$release/tunnel.pub")
authorized=/home/booking-tunnel/.ssh/authorized_keys
touch "$authorized"
if ! grep -Fq "$key" "$authorized"; then
  printf 'restrict,port-forwarding,permitopen="127.0.0.1:18080",command="/bin/false" %s\n' "$key" >> "$authorized"
fi
chown booking-tunnel:booking-tunnel "$authorized"
chmod 600 "$authorized" "$root/.env"
if command -v restorecon >/dev/null 2>&1; then restorecon -R /home/booking-tunnel/.ssh; fi
chmod 755 "$root/releases/$release/"*.sh
printf 'Restricted tunnel account and deployment permissions ready\n'
