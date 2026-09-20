#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_FOLDER=""
MODE=""
PORTAL_PORT=3000
API_PORT=8080
RELEASE_TAG=v1.0.0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --folder) WORKSPACE_FOLDER=${2:?--folder requires a path}; shift 2 ;;
    --demo) MODE=demo; shift ;;
    --empty) MODE=empty; shift ;;
    --portal-port) PORTAL_PORT=${2:?--portal-port requires a value}; shift 2 ;;
    --api-port) API_PORT=${2:?--api-port requires a value}; shift 2 ;;
    --release-tag) RELEASE_TAG=${2:?--release-tag requires a value}; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

printf '%s\n' "$RELEASE_TAG" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$' || { echo 'Release tag must use vMAJOR.MINOR.PATCH.' >&2; exit 2; }
[ "$PORTAL_PORT" != "$API_PORT" ] || { echo 'Portal and API ports must be different.' >&2; exit 2; }

DEFAULT_WORKSPACE="$PROJECT_ROOT/workspace"
if [ -z "$WORKSPACE_FOLDER" ]; then
  printf 'Workspace folder [%s]: ' "$DEFAULT_WORKSPACE"
  read -r WORKSPACE_FOLDER
  WORKSPACE_FOLDER=${WORKSPACE_FOLDER:-$DEFAULT_WORKSPACE}
fi

REPOSITORY=DhruvJawalkar/job-search-command-center
ARCHIVE="job-search-command-center-$RELEASE_TAG-standalone.tar.gz"
CHECKSUMS="job-search-command-center-$RELEASE_TAG-standalone.SHA256SUMS"
RELEASE_BASE="https://github.com/$REPOSITORY/releases/download/$RELEASE_TAG"
TEMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/jscc-bootstrap.XXXXXX")
trap 'rm -rf -- "$TEMP_ROOT"' EXIT HUP INT TERM

printf '\nJob Search Command Center — Codex-assisted runtime setup\n'
printf 'Accepted release: %s\nLocal workspace: %s\n' "$RELEASE_TAG" "$WORKSPACE_FOLDER"
printf '%s\n' 'The bootstrap will download the accepted source-free bundle, verify its checksum, pull immutable release images, and start the local app.'
printf '%s\n\n' 'Expected time: 3–8 minutes on the first run.'

download() {
  url=$1
  destination=$2
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 "$url" --output "$destination"
  elif command -v wget >/dev/null 2>&1; then
    wget --https-only --quiet "$url" --output-document "$destination"
  else
    echo 'curl or wget is required to download the accepted release.' >&2
    exit 1
  fi
}

if ! download "$RELEASE_BASE/$ARCHIVE" "$TEMP_ROOT/$ARCHIVE" || ! download "$RELEASE_BASE/$CHECKSUMS" "$TEMP_ROOT/$CHECKSUMS"; then
  echo "The accepted $RELEASE_TAG release bundle is not available from GitHub. No application was installed." >&2
  exit 1
fi

EXPECTED=$(awk -v name="$ARCHIVE" '$2 == name || $2 == "*" name { print $1; exit }' "$TEMP_ROOT/$CHECKSUMS")
[ -n "$EXPECTED" ] || { echo "The published checksum file does not contain $ARCHIVE." >&2; exit 1; }
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$TEMP_ROOT/$ARCHIVE" | awk '{print $1}')
else
  ACTUAL=$(shasum -a 256 "$TEMP_ROOT/$ARCHIVE" | awk '{print $1}')
fi
[ "$ACTUAL" = "$EXPECTED" ] || { echo 'Release archive checksum mismatch. The archive was not executed.' >&2; exit 1; }

mkdir -p "$TEMP_ROOT/bundle"
tar -xzf "$TEMP_ROOT/$ARCHIVE" -C "$TEMP_ROOT/bundle"
[ -f "$TEMP_ROOT/bundle/setup.sh" ] || { echo 'The verified release archive does not contain setup.sh.' >&2; exit 1; }

set -- --folder "$WORKSPACE_FOLDER" --portal-port "$PORTAL_PORT" --api-port "$API_PORT" --distribution-channel codex
case "$MODE" in demo) set -- "$@" --demo ;; empty) set -- "$@" --empty ;; esac
sh "$TEMP_ROOT/bundle/setup.sh" "$@"
