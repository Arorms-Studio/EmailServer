#!/usr/bin/env bash
# run-all.sh — Run every step in order. Stops on the first non-zero exit.
# Requires: jq, python3, curl. Service must already be running on BASE_URL.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for s in 01-register.sh 02-login.sh 03-me.sh 04-refresh.sh 05-send-mail.sh; do
  echo
  echo "################ $s ################"
  bash "$SCRIPT_DIR/$s"
done

echo
echo "All steps finished."
