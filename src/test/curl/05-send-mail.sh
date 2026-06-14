#!/usr/bin/env bash
# 05-send-mail.sh — POST /api/mail/send
# Reads the access token saved by 02-login.sh / 01-register.sh / 04-refresh.sh.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "05 — Send mail (POST /api/mail/send)"

if [[ ! -s "$SCRIPT_DIR/$ACCESS_TOKEN_FILE" ]]; then
  echo "Missing access token. Run 02-login.sh (or 01-register.sh / 04-refresh.sh) first." >&2
  exit 1
fi

access=$(cat "$SCRIPT_DIR/$ACCESS_TOKEN_FILE")
echo "Using access token (first 32 chars): ${access:0:32}..."
echo

body=$(jq -n --arg to "$MAIL_TO" --arg s "$MAIL_SUBJECT" --arg c "$MAIL_CONTENT" \
  '{to: $to, subject: $s, content: $c}')

echo "Request body: $body"
echo

# Note: do NOT --fail-with-body here. The server may return 500 if no local
# SMTP is listening; we still want to see the body to diagnose.
curl -sS -X POST "$BASE_URL/api/mail/send" \
  -H "Authorization: Bearer $access" \
  -H "Content-Type: application/json" \
  -d "$body" \
  -w '\nHTTP %{http_code}\n'
