#!/usr/bin/env bash
# 02-login.sh — POST /api/auth/login
# Persists the access token and the refresh-cookie jar for downstream scripts.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "02 — Log in (POST /api/auth/login)"

body=$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" \
  '{username: $u, password: $p}')

echo "Request body: $body"
echo

RESP_HEADERS="/tmp/curl-headers.$$"
RESP_BODY="/tmp/curl-body.$$"
code=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "$body" \
  -D "$RESP_HEADERS" \
  -o "$RESP_BODY" \
  -w '%{http_code}')

echo "--- response body ---"
cat "$RESP_BODY"; echo
echo "--- response headers ---"
cat "$RESP_HEADERS"
echo "HTTP $code"

if [[ "$code" != "200" ]]; then
  echo "Login failed (HTTP $code)" >&2
  rm -f "$RESP_HEADERS" "$RESP_BODY"
  exit 1
fi

access=$(access_token_from_json < "$RESP_BODY")
cookie_header=$(grep -i '^set-cookie:' "$RESP_HEADERS" | head -1 || true)
refresh=$(refresh_token_from_cookie "$cookie_header")

echo "$access"  > "$SCRIPT_DIR/$ACCESS_TOKEN_FILE"
echo "$refresh" > "$SCRIPT_DIR/$REFRESH_TOKEN_FILE"
echo "# Netscape HTTP Cookie File" > "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"
echo -e "#HttpOnly_localhost\tFALSE\t/\tFALSE\t0\tREFRESH_TOKEN\t$refresh" \
  >> "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"
chmod 600 "$SCRIPT_DIR/$ACCESS_TOKEN_FILE" "$SCRIPT_DIR/$REFRESH_TOKEN_FILE" "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"

echo
echo "Saved access token  → $SCRIPT_DIR/$ACCESS_TOKEN_FILE"
echo "Saved refresh token → $SCRIPT_DIR/$REFRESH_TOKEN_FILE"
echo "Saved cookie jar    → $SCRIPT_DIR/$REFRESH_COOKIE_FILE"

rm -f "$RESP_HEADERS" "$RESP_BODY"
