#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
DEFAULT_FOLDER="$SCRIPT_DIR/workspace"
PROJECT_FOLDER=""
MODE=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --folder) PROJECT_FOLDER=${2:?--folder requires a path}; shift 2 ;;
    --demo) MODE=demo; shift ;;
    --empty) MODE=empty; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

printf '\nJob Search Command Center — local setup\n'
printf '%s\n' 'Recommended Codex project setup:'
printf '%s\n' '  1. In Codex, create a local project named "Job Search Command Center".'
printf '  2. Add this repository folder and make it the primary folder: %s\n' "$SCRIPT_DIR"
printf '%s\n\n' '  3. Start a chat in that project. After setup, paste the prompt shown at the end.'
printf '%s\n' 'This will:'
printf '%s\n' '  1. Create a private local workspace and configuration.'
printf '%s\n' '  2. Build PostgreSQL, API, and portal containers.'
printf '%s\n' '  3. Start the app on http://127.0.0.1:3000.'
printf '%s\n' 'Expected time: 5–10 minutes on the first run; later starts are faster.'
printf '%s\n\n' 'Needed: Docker Desktop or Docker Engine with Compose and about 3 GB of free disk space.'

if [ -z "$PROJECT_FOLDER" ]; then
  printf 'Workspace folder [%s]: ' "$DEFAULT_FOLDER"
  read -r PROJECT_FOLDER
  PROJECT_FOLDER=${PROJECT_FOLDER:-$DEFAULT_FOLDER}
fi
case "$PROJECT_FOLDER" in *'#'*) echo 'The workspace folder cannot contain a # character.' >&2; exit 2 ;; esac
mkdir -p "$PROJECT_FOLDER"
PROJECT_FOLDER=$(CDPATH= cd -- "$PROJECT_FOLDER" && pwd)

if [ -z "$MODE" ]; then
  printf '%s\n' 'Choose the first-run experience:'
  printf '%s\n' '  1. Start empty (recommended for personal use)'
  printf '%s\n' '  2. Load synthetic demo content'
  printf 'Selection [1]: '
  read -r CHOICE
  if [ "${CHOICE:-1}" = 2 ]; then MODE=demo; else MODE=empty; fi
fi

command -v docker >/dev/null 2>&1 || { echo 'Docker was not found. Install and start Docker, then run this script again.' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker is installed but is not running.' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'Docker Compose v2 is required.' >&2; exit 1; }

for folder in postgres-data application-resumes notes daily-high-fit-job-roles linkedin-data-import preparation-workspace company-targets backups; do
  mkdir -p "$PROJECT_FOLDER/$folder"
done

ENV_FILE="$PROJECT_FOLDER/.env"
if [ -f "$ENV_FILE" ]; then
  printf 'Reusing existing local configuration: %s\n' "$ENV_FILE"
else
  PASSWORD=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
  if [ "$MODE" = demo ]; then DEMO=true; else DEMO=false; fi
  {
    printf 'APP_DATA_ROOT=%s\n' "$PROJECT_FOLDER"
    printf 'APP_UID=%s\nAPP_GID=%s\n' "$(id -u)" "$(id -g)"
    printf '%s\n' 'POSTGRES_DB=job_search' 'POSTGRES_USER=job_search'
    printf 'POSTGRES_PASSWORD=%s\n' "$PASSWORD"
    printf 'APP_SEED_DEMO=%s\nAPP_DEMO_MODE=%s\n' "$DEMO" "$DEMO"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

if [ "$PROJECT_FOLDER" != "$SCRIPT_DIR" ]; then
  cp "$SCRIPT_DIR/docs/LOCAL_WORKSPACE_README.md" "$PROJECT_FOLDER/README.md"
fi

printf '\nBuilding and starting the local app…\n'
docker compose --project-name job-search-command-center --env-file "$ENV_FILE" --file "$COMPOSE_FILE" up --detach --build --wait

printf '\nSetup complete.\n'
printf 'Workspace folder: %s\n' "$PROJECT_FOLDER"
printf '%s\n' 'App: http://127.0.0.1:3000' 'API health: http://127.0.0.1:8080/actuator/health'
printf '\n%s\n' 'Next in Codex:'
printf '%s\n' '  1. Return to the Job Search Command Center project chat.'
printf '%s\n' '  2. Open a Browser tab in the right-side panel and enter http://127.0.0.1:3000.'
printf '%s\n' '  3. Ask: “Review README.md and docs/CODEX_ONBOARDING.md, use the open portal tab, and guide me through the first useful outcome.”'
