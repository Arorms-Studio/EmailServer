#!/usr/bin/env bash
# 01-register.sh — POST /api/auth/register
# Auto-login on success: response body is a TokenResponse, Set-Cookie carries
# REFRESH_TOKEN. First run creates the user; a re-run returns 409 (asserted).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "01 — Register a new user (POST /api/auth/register)"

body=$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" \
  '{username: $u, password: $p}')

echo "Request body: $body"
echo

# Capture both body and Set-Cookie via -D (dump headers to stdout) and
# -o (body to file).
RESP_HEADERS="/tmp/curl-headers.$$"
RESP_BODY="/tmp/curl-body.$$"
code=$(curl -sS -X POST "$BASE_URL/api/auth/register" \
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

case "$code" in
  200)
    # First-run success: extract and persist tokens.
    access=$(access_token_from_json < "$RESP_BODY")
    cookie_header=$(grep -i '^set-cookie:' "$RESP_HEADERS" | head -1 || true)
    refresh=$(refresh_token_from_cookie "$cookie_header")

    echo "$access"  > "$SCRIPT_DIR/$ACCESS_TOKEN_FILE"
    echo "$refresh" > "$SCRIPT_DIR/$REFRESH_TOKEN_FILE"
    # Also save a curl-format cookie file so 04-refresh.sh can use -b.
    echo "# Netscape HTTP Cookie File" > "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"
    echo -e "#HttpOnly_localhost\tFALSE\t/\tFALSE\t0\tREFRESH_TOKEN\t$refresh" \
      >> "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"
    chmod 600 "$SCRIPT_DIR/$ACCESS_TOKEN_FILE" "$SCRIPT_DIR/$REFRESH_TOKEN_FILE" "$SCRIPT_DIR/$REFRESH_COOKIE_FILE"

    echo
    echo "Saved access token  → $SCRIPT_DIR/$ACCESS_TOKEN_FILE"
    echo "Saved refresh token → $SCRIPT_DIR/$REFRESH_TOKEN_FILE"
    echo "Saved cookie jar    → $SCRIPT_DIR/$REFRESH_COOKIE_FILE"
    ;;
  409)
    # Re-run: user already exists. That is also a documented outcome, not a failure.
    echo
    echo "User '$USERNAME' already exists (HTTP 409) — skipping token persistence."
    echo "If you want a fresh user, change USERNAME in .env.test or delete the row from the DB."
    ;;
  *)
    echo "Unexpected status $code" >&2
    rm -f "$RESP_HEADERS" "$RESP_BODY"
    exit 1
    ;;
esac

rm -f "$RESP_HEADERS" "$RESP_BODY"
