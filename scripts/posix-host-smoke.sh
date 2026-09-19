#!/usr/bin/env sh
# Disposable macOS/Linux host acceptance for a fresh local installation.
# It deliberately never targets the normal project name or a caller-provided workspace.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MODE=empty
KEEP_STACK=false
KEEP_WORKSPACE=true
API_PORT=18080
PORTAL_PORT=13000
EVIDENCE_DIR=

usage() {
  cat <<'EOF'
Usage: ./scripts/posix-host-smoke.sh [--empty|--demo] [--api-port PORT] [--portal-port PORT]
                                    [--keep-stack] [--remove-workspace] [--evidence-dir PATH]

Runs setup.sh in a newly-created, uniquely labelled disposable workspace and Compose project.
The default cleans up only that project stack (never volumes) and retains the workspace with
smoke-evidence.txt and smoke-evidence.json. --remove-workspace removes only the workspace this
script created; use --evidence-dir to keep a copy of the evidence before doing so.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --empty) MODE=empty ;;
    --demo) MODE=demo ;;
    --api-port) API_PORT=${2:?--api-port requires a port}; shift ;;
    --portal-port) PORTAL_PORT=${2:?--portal-port requires a port}; shift ;;
    --keep-stack) KEEP_STACK=true ;;
    --remove-workspace) KEEP_WORKSPACE=false ;;
    --evidence-dir) EVIDENCE_DIR=${2:?--evidence-dir requires a path}; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

case "$API_PORT:$PORTAL_PORT" in *[!0-9:]*|:*) echo 'Ports must be numeric.' >&2; exit 2;; esac
[ "$API_PORT" != "$PORTAL_PORT" ] || { echo 'API and portal ports must differ.' >&2; exit 2; }

need() { command -v "$1" >/dev/null 2>&1 || { printf 'Missing prerequisite: %s\n' "$1" >&2; exit 1; }; }
need git; need docker; need curl; need awk; need grep; need id; need stat
docker info >/dev/null 2>&1 || { echo 'Docker is not running.' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'Docker Compose v2 is required.' >&2; exit 1; }
git -C "$ROOT" rev-parse --is-inside-work-tree | grep -qx true || { echo 'Run from a Git clone.' >&2; exit 1; }
[ -x "$ROOT/setup.sh" ] || { echo 'setup.sh is not executable; run chmod +x setup.sh.' >&2; exit 1; }

port_free() {
  # bind is ultimately enforced by Docker; this preflight avoids disturbing a local installation.
  if command -v lsof >/dev/null 2>&1; then ! lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v ss >/dev/null 2>&1; then ! ss -ltn "sport = :$1" | grep -q LISTEN
  else return 0
  fi
}
port_free "$API_PORT" || { echo "API port $API_PORT is already in use." >&2; exit 1; }
port_free "$PORTAL_PORT" || { echo "Portal port $PORTAL_PORT is already in use." >&2; exit 1; }

STAMP=$(date -u +%Y%m%dt%H%M%Sz)-$$
PROJECT="jscc-posix-smoke-$STAMP"
WORKSPACE="$ROOT/tmp/$PROJECT"
case "$WORKSPACE" in "$ROOT"/tmp/jscc-posix-smoke-*) ;; *) echo 'Unsafe generated workspace path.' >&2; exit 1;; esac
mkdir -p "$WORKSPACE"
EVIDENCE="$WORKSPACE/smoke-evidence.txt"
REPORT="$WORKSPACE/smoke-evidence.json"
touch "$EVIDENCE"

say() { printf '%s\n' "$*" | tee -a "$EVIDENCE"; }
pass() { say "PASS: $1"; }
fail() { say "FAIL: $1"; exit 1; }
compose() { docker compose --project-name "$PROJECT" --env-file "$WORKSPACE/.env" --file "$ROOT/compose.yaml" "$@"; }
retry_curl() {
  url=$1; tries=${2:-45}; n=0
  while [ "$n" -lt "$tries" ]; do
    if curl --fail --silent --show-error --max-time 5 "$url" >/dev/null 2>&1; then return 0; fi
    n=$((n + 1)); sleep 1
  done
  return 1
}
cleanup() {
  status=$?
  if [ "$KEEP_STACK" = false ]; then
    # No --volumes: bind-mounted data and Compose volumes are deliberately retained.
    docker compose --project-name "$PROJECT" --env-file "$WORKSPACE/.env" --file "$ROOT/compose.yaml" down --remove-orphans >/dev/null 2>&1 || true
  fi
  if [ -n "$EVIDENCE_DIR" ] && [ -d "$EVIDENCE_DIR" ]; then
    cp "$EVIDENCE" "$EVIDENCE_DIR/$PROJECT-smoke-evidence.txt" 2>/dev/null || true
    cp "$REPORT" "$EVIDENCE_DIR/$PROJECT-smoke-evidence.json" 2>/dev/null || true
  fi
  if [ "$KEEP_WORKSPACE" = false ]; then
    case "$WORKSPACE" in "$ROOT"/tmp/jscc-posix-smoke-*) rm -rf "$WORKSPACE";; esac
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

say "POSIX host smoke started: $(date -u +%FT%TZ)"
say "project=$PROJECT workspace=$WORKSPACE mode=$MODE apiPort=$API_PORT portalPort=$PORTAL_PORT"
say "clone=$(git -C "$ROOT" rev-parse --show-toplevel)"

JSCC_COMPOSE_PROJECT_NAME="$PROJECT" JSCC_API_HOST_PORT="$API_PORT" JSCC_PORTAL_HOST_PORT="$PORTAL_PORT" \
  "$ROOT/setup.sh" --folder "$WORKSPACE" "--$MODE" >>"$EVIDENCE" 2>&1 || fail 'non-interactive setup.sh failed'
pass 'non-interactive setup completed'

[ -f "$WORKSPACE/.env" ] || fail '.env was not created'
env_mode=$(stat -c %a "$WORKSPACE/.env" 2>/dev/null || stat -f %Lp "$WORKSPACE/.env")
[ "$env_mode" = 600 ] || fail ".env permissions expected 600, found $env_mode"
pass '.env is owner-readable only (0600)'

for d in postgres-data application-resumes notes daily-high-fit-job-roles linkedin-data-import preparation-workspace company-targets backups; do
  [ -d "$WORKSPACE/$d" ] || fail "workspace folder missing: $d"
done
printf 'host-marker-%s\n' "$STAMP" > "$WORKSPACE/notes/host-smoke-marker.txt"
compose exec -T api sh -ec 'printf container-marker > /workspace/notes/container-smoke-marker.txt; test -f /workspace/notes/host-smoke-marker.txt' >>"$EVIDENCE" 2>&1 || fail 'bind-mounted workspace is not writable/readable by API user'
[ -f "$WORKSPACE/notes/container-smoke-marker.txt" ] || fail 'API bind-mount write is not visible on host'
container_ids=$(compose exec -T api sh -ec 'printf "%s:%s" "$(id -u)" "$(id -g)"' | tr -d '\r')
host_ids="$(id -u):$(id -g)"
[ "$container_ids" = "$host_ids" ] || fail "API identity expected $host_ids, found $container_ids"
if [ "$(uname -s)" = Linux ]; then
  marker_ids=$(stat -c '%u:%g' "$WORKSPACE/notes/container-smoke-marker.txt")
  [ "$marker_ids" = "$host_ids" ] || fail "API-written file ownership expected $host_ids, found $marker_ids"
  pass 'API identity and bind-mounted file ownership match the invoking Linux user'
else
  pass 'API identity matches the invoking user; Docker Desktop bind-mount read-write contract passed'
fi
pass 'host/API bind-mount read-write contract'

for service in postgres api portal gateway; do
  id=$(compose ps -q "$service")
  [ -n "$id" ] || fail "$service container is absent"
  state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id")
  say "$service=$state"
done
pass 'all four services exist after setup'

retry_curl "http://127.0.0.1:$API_PORT/actuator/health" || fail 'API health is not reachable through gateway'
retry_curl "http://127.0.0.1:$PORTAL_PORT/" || fail 'portal root is not browser-reachable through gateway'
curl --fail --silent --max-time 10 "http://127.0.0.1:$API_PORT/api/v1/installation" > "$WORKSPACE/installation.json" || fail 'installation endpoint failed'
curl --fail --silent --max-time 10 "http://127.0.0.1:$API_PORT/api/v1/opportunities" > "$WORKSPACE/opportunities.json" || fail 'opportunity endpoint failed'
curl --fail --silent --max-time 10 "http://127.0.0.1:$API_PORT/api/v1/privacy-policy" > "$WORKSPACE/initial-privacy-policy.json" || fail 'privacy-policy endpoint failed'
grep -q '"consentAcceptedAt":null' "$WORKSPACE/initial-privacy-policy.json" || fail 'fresh install unexpectedly has privacy notice acceptance'
grep -q '"connectedAssistanceEnabled":false' "$WORKSPACE/initial-privacy-policy.json" || fail 'fresh install unexpectedly enables connected assistance'
grep -q '"currentNoticeVersion":"v1"' "$WORKSPACE/initial-privacy-policy.json" || fail 'fresh install did not report privacy notice v1'
pass 'fresh install requires privacy notice acceptance and starts local-only'
grep -q '"demoMode":' "$WORKSPACE/installation.json" || fail 'installation endpoint did not report data mode'
if [ "$MODE" = demo ]; then
  grep -q '"demoMode":true' "$WORKSPACE/installation.json" || fail 'demo mode was not reported'
  for company in 'Northstar Systems' 'Cobalt Cloud' 'FinPeak'; do
    grep -q "\"companyName\":\"$company\"" "$WORKSPACE/opportunities.json" || fail "demo opportunity missing: $company"
  done
else
  grep -q '"demoMode":false' "$WORKSPACE/installation.json" || fail 'empty mode was not reported'
  [ "$(tr -d '[:space:]' < "$WORKSPACE/opportunities.json")" = '[]' ] || fail 'empty mode unexpectedly contains opportunities'
fi
pass 'browser-facing gateway endpoints and installation data mode'

gateway_id=$(compose ps -q gateway)
api_id=$(compose ps -q api)
portal_id=$(compose ps -q portal)
postgres_id=$(compose ps -q postgres)
for pair in "postgres:$postgres_id" "api:$api_id" "portal:$portal_id"; do
  service=${pair%%:*}; id=${pair#*:}
  bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$id")
  [ "$bindings" = '{}' ] || fail "$service has a direct host port binding: $bindings"
done
gateway_bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$gateway_id")
printf '%s' "$gateway_bindings" | grep -q '127.0.0.1' || fail 'gateway is not loopback-bound'
printf '%s' "$gateway_bindings" | grep -q "\"HostPort\":\"$API_PORT\"" || fail 'gateway API port differs from requested isolated port'
printf '%s' "$gateway_bindings" | grep -q "\"HostPort\":\"$PORTAL_PORT\"" || fail 'gateway portal port differs from requested isolated port'
pass 'gateway is the only service with isolated loopback host ports'

migrations=$(compose exec -T postgres psql -X -qAt -U job_search -d job_search -c 'SELECT count(*) FROM flyway_schema_history WHERE success;' | tr -d '\r')
[ "$migrations" = 34 ] || fail "expected 34 successful migrations, found $migrations"
pass '34 Flyway migrations applied'

network=$(docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}} {{end}}' "$api_id" | awk '{print $1}')
[ -n "$network" ] || fail 'API local_only network was not found'
[ "$(docker network inspect --format '{{.Internal}}' "$network")" = true ] || fail 'API network is not internal'
if compose exec -T api sh -ec 'getent hosts example.com >/dev/null 2>&1 || curl --fail --silent --connect-timeout 4 https://example.com >/dev/null 2>&1'; then
  fail 'API data-service egress probe unexpectedly succeeded'
fi
if compose exec -T portal node -e "fetch('https://example.com',{signal:AbortSignal.timeout(4000)}).then(()=>process.exit(0)).catch(()=>process.exit(1))"; then
  fail 'portal data-service egress probe unexpectedly succeeded'
fi
pass 'local_only data-service egress probes fail (DNS/HTTPS)'

compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U job_search -d job_search -c "CREATE TABLE IF NOT EXISTS posix_smoke_persistence (marker text primary key); INSERT INTO posix_smoke_persistence VALUES ('$STAMP') ON CONFLICT DO NOTHING;" >>"$EVIDENCE" 2>&1
compose stop >>"$EVIDENCE" 2>&1 && compose up --detach --wait >>"$EVIDENCE" 2>&1 || fail 'stop/start persistence cycle failed'
compose exec -T postgres psql -X -qAt -U job_search -d job_search -c "SELECT marker FROM posix_smoke_persistence WHERE marker = '$STAMP';" | grep -qx "$STAMP" || fail 'database marker did not survive restart'
[ -f "$WORKSPACE/notes/container-smoke-marker.txt" ] || fail 'workspace marker did not survive restart'
pass 'database and bind-mounted workspace persist across stop/start'

compose up --detach --wait --force-recreate api portal >>"$EVIDENCE" 2>&1 || fail 'API/portal recreation failed'
retry_curl "http://127.0.0.1:$API_PORT/actuator/health" 50 || fail 'gateway did not recover API route after recreation'
retry_curl "http://127.0.0.1:$PORTAL_PORT/" 50 || fail 'gateway did not recover portal route after recreation'
pass 'gateway recovers fixed routes after API/portal recreation'

cat > "$REPORT" <<EOF
{"passed":true,"checkedAt":"$(date -u +%FT%TZ)","project":"$PROJECT","workspace":"$WORKSPACE","mode":"$MODE","apiPort":$API_PORT,"portalPort":$PORTAL_PORT,"migrations":$migrations,"manualCheckpoint":"Open the portal in a real browser and record that the first-run Privacy & intelligence modal appears; choose and save a policy manually. This harness intentionally does not automate that consent decision."}
EOF
say "PASS: smoke complete; evidence=$EVIDENCE report=$REPORT"
