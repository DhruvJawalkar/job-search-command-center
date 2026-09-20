#!/usr/bin/env sh
set -eu

CONTROL_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_ROOT=$(CDPATH= cd -- "$CONTROL_ROOT/.." && pwd)
COMPOSE_FILE="$CONTROL_ROOT/compose.yaml"
ENV_FILE="$WORKSPACE_ROOT/.env"
COMMAND=${1:-status}

case "$COMMAND" in start|stop|restart|status|logs|pull) ;; *) echo "Usage: $0 {start|stop|restart|status|logs|pull}" >&2; exit 2 ;; esac
[ -f "$COMPOSE_FILE" ] || { echo "Runtime definition not found: $COMPOSE_FILE" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "Workspace configuration not found: $ENV_FILE" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo 'Docker was not found.' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker is installed but is not running.' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'Docker Compose v2 is required.' >&2; exit 1; }

PROJECT_NAME=$(sed -n 's/^COMPOSE_PROJECT_NAME=//p' "$ENV_FILE" | sed -n '1p')
case "$PROJECT_NAME" in ''|*[!a-z0-9_.-]*|[!a-z0-9]*) echo 'The stored Compose project name is invalid.' >&2; exit 1 ;; esac
export APP_DATA_ROOT=$WORKSPACE_ROOT

compose() {
  docker compose --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" --file "$COMPOSE_FILE" "$@"
}

case "$COMMAND" in
  start) compose pull && compose up --detach --pull never --no-build --wait ;;
  stop) compose stop ;;
  restart) compose stop && compose up --detach --pull never --no-build --wait ;;
  status) compose ps ;;
  logs) compose logs --tail 200 ;;
  pull) compose pull ;;
esac
