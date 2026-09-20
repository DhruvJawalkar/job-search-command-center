#!/usr/bin/env sh
set -eu

BUNDLE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_FOLDER=""
MODE=""
PORTAL_PORT=3000
API_PORT=8080
DISTRIBUTION_CHANNEL=standalone

case "$(uname -s)" in
  Darwin) DEFAULT_FOLDER="$HOME/Library/Application Support/JobSearchCommandCenter" ;;
  *) DEFAULT_FOLDER="${XDG_DATA_HOME:-$HOME/.local/share}/job-search-command-center" ;;
esac

while [ "$#" -gt 0 ]; do
  case "$1" in
    --folder) WORKSPACE_FOLDER=${2:?--folder requires a path}; shift 2 ;;
    --demo) MODE=demo; shift ;;
    --empty) MODE=empty; shift ;;
    --portal-port) PORTAL_PORT=${2:?--portal-port requires a value}; shift 2 ;;
    --api-port) API_PORT=${2:?--api-port requires a value}; shift 2 ;;
    --distribution-channel) DISTRIBUTION_CHANNEL=${2:?--distribution-channel requires a value}; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

case "$DISTRIBUTION_CHANNEL" in
  standalone|codex) ;;
  *) echo 'Distribution channel must be standalone or codex.' >&2; exit 2 ;;
esac

printf '\nJob Search Command Center — published Docker setup\n'
printf '%s\n' 'This source-free installer will:'
printf '%s\n' '  1. Create a private local workspace and generated database password.'
printf '%s\n' '  2. Pull the signed release images; it will not build application source.'
printf '%s\n\n' '  3. Start the app on the loopback-only local URL.'

if [ -z "$WORKSPACE_FOLDER" ]; then
  printf 'Workspace folder [%s]: ' "$DEFAULT_FOLDER"
  read -r WORKSPACE_FOLDER
  WORKSPACE_FOLDER=${WORKSPACE_FOLDER:-$DEFAULT_FOLDER}
fi
case "$WORKSPACE_FOLDER" in *'#'*) echo 'The workspace folder cannot contain a # character.' >&2; exit 2 ;; esac

if [ -z "$MODE" ]; then
  printf '%s\n' 'Choose the first-run experience:' '  1. Start empty (recommended for personal use)' '  2. Load synthetic demo content'
  printf 'Selection [1]: '
  read -r CHOICE
  if [ "${CHOICE:-1}" = 2 ]; then MODE=demo; else MODE=empty; fi
fi
case "$PORTAL_PORT:$API_PORT" in *[!0-9:]*|:*) echo 'Ports must be numeric.' >&2; exit 2 ;; esac
[ "$PORTAL_PORT" -ge 1 ] 2>/dev/null && [ "$PORTAL_PORT" -le 65535 ] || { echo 'Portal port must be between 1 and 65535.' >&2; exit 2; }
[ "$API_PORT" -ge 1 ] 2>/dev/null && [ "$API_PORT" -le 65535 ] || { echo 'API port must be between 1 and 65535.' >&2; exit 2; }
[ "$PORTAL_PORT" != "$API_PORT" ] || { echo 'Portal and API ports must be different.' >&2; exit 2; }

for required in compose.yaml gateway/nginx.conf release-manifest.json VERSION jscc.ps1 jscc.sh; do
  [ -f "$BUNDLE_ROOT/$required" ] || { echo "Release bundle is incomplete: $required is missing." >&2; exit 1; }
done
command -v docker >/dev/null 2>&1 || { echo 'Docker was not found. Install and start Docker, then run this script again.' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker is installed but is not running.' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'Docker Compose v2 is required.' >&2; exit 1; }

mkdir -p "$WORKSPACE_FOLDER"
WORKSPACE_FOLDER=$(CDPATH= cd -- "$WORKSPACE_FOLDER" && pwd)
for folder in postgres-data application-resumes notes daily-high-fit-job-roles linkedin-data-import preparation-workspace company-targets backups; do
  mkdir -p "$WORKSPACE_FOLDER/$folder"
done

ENV_FILE="$WORKSPACE_FOLDER/.env"
if [ -f "$ENV_FILE" ]; then
  STORED_MODE=$(sed -n 's/^APP_DEMO_MODE=//p' "$ENV_FILE" | sed -n '1p')
  [ -n "$STORED_MODE" ] || { echo 'The existing workspace .env has no APP_DEMO_MODE value.' >&2; exit 1; }
  REQUESTED=false; [ "$MODE" = demo ] && REQUESTED=true
  [ "$STORED_MODE" = "$REQUESTED" ] || { echo 'This workspace was initialized in a different data mode. Choose another workspace folder.' >&2; exit 1; }
  printf 'Reusing existing local configuration: %s\n' "$ENV_FILE"
else
  if command -v sha256sum >/dev/null 2>&1; then
    HASH=$(printf '%s' "$WORKSPACE_FOLDER" | sha256sum | cut -c1-8)
  else
    HASH=$(printf '%s' "$WORKSPACE_FOLDER" | shasum -a 256 | cut -c1-8)
  fi
  PASSWORD=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
  VERSION=$(sed -n '1p' "$BUNDLE_ROOT/VERSION" | sed 's/^v//')
  DEMO=false; [ "$MODE" = demo ] && DEMO=true
  {
    printf 'COMPOSE_PROJECT_NAME=job-search-command-center-%s\n' "$HASH"
    printf 'APP_UID=%s\nAPP_GID=%s\n' "$(id -u)" "$(id -g)"
    printf '%s\n' 'POSTGRES_DB=job_search' 'POSTGRES_USER=job_search'
    printf 'POSTGRES_PASSWORD=%s\n' "$PASSWORD"
    printf 'APP_SEED_DEMO=%s\nAPP_DEMO_MODE=%s\n' "$DEMO" "$DEMO"
    printf 'APP_API_HOST_PORT=%s\nAPP_PORTAL_HOST_PORT=%s\n' "$API_PORT" "$PORTAL_PORT"
    printf 'APP_VERSION=%s\n' "$VERSION"
    printf 'APP_DISTRIBUTION_CHANNEL=%s\n' "$DISTRIBUTION_CHANNEL"
    if [ "$DISTRIBUTION_CHANNEL" = codex ]; then
      printf '%s\n' 'APP_CODEX_GUIDANCE_ENABLED=true'
    else
      printf '%s\n' 'APP_CODEX_GUIDANCE_ENABLED=false'
    fi
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

CONTROL_ROOT="$WORKSPACE_FOLDER/.jscc"
mkdir -p "$CONTROL_ROOT/gateway"
for file in compose.yaml compose.connected.yaml release-manifest.json SHA256SUMS VERSION LICENSE NOTICE TRADEMARKS.md COMMERCIAL-LICENSE.md jscc.ps1 jscc.sh; do
  [ ! -f "$BUNDLE_ROOT/$file" ] || cp "$BUNDLE_ROOT/$file" "$CONTROL_ROOT/$file"
done
cp "$BUNDLE_ROOT/gateway/nginx.conf" "$CONTROL_ROOT/gateway/nginx.conf"
[ ! -f "$BUNDLE_ROOT/README.md" ] || cp "$BUNDLE_ROOT/README.md" "$WORKSPACE_FOLDER/README.md"
chmod +x "$CONTROL_ROOT/jscc.sh"

printf '\nPulling and starting the accepted release…\n'
"$CONTROL_ROOT/jscc.sh" start
printf '\nSetup complete.\nWorkspace folder: %s\nApp: http://127.0.0.1:%s\n' "$WORKSPACE_FOLDER" "$PORTAL_PORT"
printf 'Status: %s status\nStop: %s stop\nLogs: %s logs\n' "$CONTROL_ROOT/jscc.sh" "$CONTROL_ROOT/jscc.sh" "$CONTROL_ROOT/jscc.sh"
