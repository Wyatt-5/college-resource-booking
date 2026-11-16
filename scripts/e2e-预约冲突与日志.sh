#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$task_root"
exec node --env-file-if-exists=.env scripts/booking-regression.mjs
