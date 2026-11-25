#!/bin/sh
set -eu
umask 077
entry=/opt/college-booking/public-entry
lineage=${RENEWED_LINEAGE:-$entry/letsencrypt/live/booking-ip}
install -d -m 750 -o root -g 101 "$entry/tls"
install -m 644 "$lineage/fullchain.pem" "$entry/tls/fullchain.pem.next"
install -m 640 -o root -g 101 "$lineage/privkey.pem" "$entry/tls/privkey.pem.next"
mv -f "$entry/tls/fullchain.pem.next" "$entry/tls/fullchain.pem"
mv -f "$entry/tls/privkey.pem.next" "$entry/tls/privkey.pem"
if test "$(docker inspect college-booking-entry --format '{{.State.Running}}' 2>/dev/null || true)" = true; then
  docker exec college-booking-entry nginx -t
  docker exec college-booking-entry nginx -s reload
fi
