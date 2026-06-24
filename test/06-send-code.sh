#!/usr/bin/env bash
# 06-send-code.sh — POST /api/auth/send-code
# Triggers the verification-code email flow. The first call should
# return 204; the second call within 60s should return 429.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

banner "06 — Send a verification code (POST /api/auth/send-code)"

EMAIL="${REGISTRATION_EMAIL:-test@example.com}"

echo "Sending code to: $EMAIL"
http_status CODE POST /api/auth/send-code \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e}')"

case "$CODE" in
  204)
    echo
    echo "OK — 204 No Content. Check $EMAIL for the 6-digit code."
    echo "If you are running this against a real SMTP relay, retrieve the code from the email."
    echo "If you are running against a dev SMTP catch-all, the code is also in Redis at:"
    echo "  redis-cli GET \"email-server:code:email:${EMAIL,,}\""
    ;;
  429)
    echo
    echo "Already rate-limited (cooldown). Wait 60s and retry, or flush Redis:"
    echo "  redis-cli DEL \"email-server:rate:email:send-code:${EMAIL,,}\""
    ;;
  *)
    echo "Unexpected status $CODE" >&2
    exit 1
    ;;
esac
