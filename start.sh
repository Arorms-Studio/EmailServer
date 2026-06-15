#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="$(dirname "$0")/.env"
[[ -f "$ENV_FILE" ]] || { echo "missing $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

cd "$(dirname "$0")"
exec mvn -pl email-app spring-boot:run
