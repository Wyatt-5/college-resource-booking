#!/bin/sh
set -eu
umask 077
root=/opt/college-booking
entry="$root/public-entry"
image="college-booking-web:$(basename "$(readlink -f "$root/current")")"
test -s "$entry/tls/fullchain.pem"
test -s "$entry/tls/privkey.pem"
docker image inspect "$image" >/dev/null
printf 'BOOKING_WEB_IMAGE=%s\n' "$image" > "$entry/entry.env"
docker compose --env-file "$entry/entry.env" -f "$entry/compose.yaml" config --quiet
docker run --rm --network college-booking_default --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m --cap-drop ALL --security-opt no-new-privileges:true -v "$entry/nginx.conf:/etc/nginx/nginx.conf:ro" -v "$entry/proxy.conf:/etc/nginx/booking-proxy.conf:ro" -v "$entry/tls:/tls:ro" -v "$entry/acme:/acme:ro" "$image" nginx -t
stamp=$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 700 "$entry/backups"
cp -p "$root/.env" "$entry/backups/$stamp.env"
"$root/current/backup.sh"
success=0
rollback() {
  if test "$success" = 0; then
    cp -p "$entry/backups/$stamp.env" "$root/.env"
    "$root/current/compose.sh" up -d --no-deps --force-recreate --wait --wait-timeout 180 backend web || true
    docker rm -f college-booking-entry >/dev/null 2>&1 || true
    echo 'Public activation failed; previous private application configuration restored' >&2
  fi
}
trap rollback EXIT
python3 - "$root/.env" <<'PY'
import pathlib,re,sys
path=pathlib.Path(sys.argv[1])
text=path.read_text()
if re.search(r'^SESSION_COOKIE_SECURE=',text,re.M):
    text=re.sub(r'^SESSION_COOKIE_SECURE=.*$', 'SESSION_COOKIE_SECURE=true',text,flags=re.M)
else:
    text=text.rstrip()+'\nSESSION_COOKIE_SECURE=true\n'
path.write_text(text)
PY
"$root/current/compose.sh" up -d --no-deps --force-recreate --wait --wait-timeout 180 backend web
docker rm -f college-booking-entry >/dev/null 2>&1 || true
docker compose --env-file "$entry/entry.env" -f "$entry/compose.yaml" up -d --wait --wait-timeout 90
curl --fail --silent --show-error --resolve 8.155.137.77:443:127.0.0.1 https://8.155.137.77/api/v1/health
printf '17 2,14 * * * root /opt/college-booking/public-entry/renew.sh >> /opt/college-booking/public-entry/renew.log 2>&1\n' > /etc/cron.d/college-booking-certificate
chmod 644 /etc/cron.d/college-booking-certificate
if command -v restorecon >/dev/null 2>&1; then restorecon /etc/cron.d/college-booking-certificate; fi
success=1
trap - EXIT
printf '\nPublic HTTPS entry activated; certificate renewal scheduled twice daily\n'
