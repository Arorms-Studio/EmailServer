# Shared helpers for the curl test scripts.
# Source from each script: `source "$(dirname "$0")/lib.sh"`

# Resolve the directory containing this file even when the script is symlinked.
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load .env.test from the same directory.
if [[ -f "$LIB_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$LIB_DIR/.env"
  set +a
else
  echo "lib.sh: missing $LIB_DIR/.env" >&2
  exit 1
fi

# Pretty-print the request we're about to make, then run curl with sensible
# defaults (show status code + body, fail on 5xx so we notice real errors).
# Usage: http METHOD PATH [curl-args...]
http() {
  local method="$1"; shift
  local path="$1"; shift
  local url="${BASE_URL%/}${path}"
  echo "--- $method $url ---"
  # -sS: silent but show errors
  # -w '\nHTTP %{http_code}\n': print status after body
  # --fail-with-body: return non-zero on 5xx so the script aborts
  curl -sS -X "$method" "$url" \
       -H "Content-Type: application/json" \
       -w '\nHTTP %{http_code}\n' \
       --fail-with-body \
       "$@"
}

# Like http() but returns the status code separately (for tests that need
# to assert on 200 vs 409 vs 401 etc.). Echoes the body to stdout; writes
# the numeric status code to the variable named by $1.
# Usage: http_status STATUS_VAR METHOD PATH [curl-args...]
http_status() {
  local status_var="$1"; shift
  local method="$1"; shift
  local path="$1"; shift
  local url="${BASE_URL%/}${path}"
  echo "--- $method $url ---"
  local code
  code=$(curl -sS -X "$method" "$url" \
              -H "Content-Type: application/json" \
              -o /tmp/curl-body.$$ \
              -w '%{http_code}' \
              "$@")
  cat /tmp/curl-body.$$
  rm -f /tmp/curl-body.$$
  echo
  echo "HTTP $code"
  # Use printf -v to assign to caller's variable
  printf -v "$status_var" '%s' "$code"
}

# Extract the access token JSON field from a login/register response.
# Usage: access_token_from_json < json-string
access_token_from_json() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
}

# Extract the raw refresh token JWT from a Set-Cookie header value.
# Usage: refresh_token_from_cookie "<set-cookie-string>"
refresh_token_from_cookie() {
  python3 -c '
import sys, re
cookie = sys.stdin.read()
m = re.search(r"REFRESH_TOKEN=([^;]+)", cookie)
print(m.group(1) if m else "")
' <<< "$1"
}

# Pretty banner for a script.
banner() {
  echo
  echo "================================================================"
  echo "  $1"
  echo "================================================================"
}