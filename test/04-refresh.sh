#!/usr/bin/env bash
# 04-refresh.sh — POST /api/auth/refresh
# Sends the REFRESH_TOKEN cookie saved by 02-login.sh (or 01-register.sh) and
# overwrites the stored tokens with the freshly-issued pair.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "04 — Refresh tokens (POST /api/auth/refresh)"

if [[ ! -s "$SCRIPT_DIR/$REFRESH_COOKIE_FILE" ]]; then
  echo "Missing refresh cookie. Run 02-login.sh (or 01-register.sh) first." >&2
  exit 1
fi

echo "Sending cookie jar: $SCRIPT_DIR/$REFRESH_COOKIE_FILE"
cat "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"
echo

RESP_HEADERS="/tmp/curl-headers.$$"
RESP_BODY="/tmp/curl-body.$$"
code=$(curl -sS -X POST "$BASE_URL/api/auth/refresh" \
  -b "$SCRIPT_DIR/$REFRESH_COOKIE_FILE" \
  -c "$SCRIPT_DIR/$REFRESH_COOKIE_FILE" \
  -D "$RESP_HEADERS" \
  -o "$RESP_BODY" \
  -w '%{http_code}')

echo "--- response body ---"
cat "$RESP_BODY"; echo
echo "--- response headers ---"
cat "$RESP_HEADERS"
echo "HTTP $code"

if [[ "$code" != "200" ]]; then
  echo "Refresh failed (HTTP $code)" >&2
  rm -f "$RESP_HEADERS" "$RESP_BODY"
  exit 1
fi

# Persist the new tokens.
access=$(access_token_from_json < "$RESP_BODY")
cookie_header=$(grep -i '^set-cookie:' "$RESP_HEADERS" | head -1 || true)
refresh=$(refresh_token_from_cookie "$cookie_header")

echo "$access"  > "$SCRIPT_DIR/$ACCESS_TOKEN_FILE"
echo "$refresh" > "$SCRIPT_DIR/$REFRESH_TOKEN_FILE"
chmod 600 "$SCRIPT_DIR/$ACCESS_TOKEN_FILE" "$SCRIPT_DIR/$REFRESH_TOKEN_FILE"

echo
echo "Refreshed access token  → $SCRIPT_DIR/$ACCESS_TOKEN_FILE"
echo "Refreshed refresh token → $SCRIPT_DIR/$REFRESH_TOKEN_FILE"

rm -f "$RESP_HEADERS" "$RESP_BODY"