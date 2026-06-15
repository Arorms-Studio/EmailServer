#!/usr/bin/env bash
# 03-me.sh — GET /api/auth/me
# Reads the access token saved by 02-login.sh (or 01-register.sh).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "03 — Current user (GET /api/auth/me)"

if [[ ! -s "$SCRIPT_DIR/$ACCESS_TOKEN_FILE" ]]; then
  echo "Missing access token. Run 02-login.sh (or 01-register.sh) first." >&2
  exit 1
fi

access=$(cat "$SCRIPT_DIR/$ACCESS_TOKEN_FILE")
echo "Using access token (first 32 chars): ${access:0:32}..."
echo

curl -sS -X GET "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $access" \
  -w '\nHTTP %{http_code}\n'
