#!/bin/sh
set -eu
umask 077
entry=/opt/college-booking/public-entry
exec 9>"$entry/renew.lock"
flock -n 9 || exit 0
"$entry/certbot-venv/bin/certbot" renew --config-dir "$entry/letsencrypt" --work-dir "$entry/certbot-work" --logs-dir "$entry/certbot-logs" --quiet --deploy-hook "$entry/reload-certificate.sh"
