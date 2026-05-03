#!/usr/bin/env bash
# Resolve the current immutable digests for every base image referenced in the
# Dockerfile and print drop-in `FROM image:tag@sha256:…` lines. Use the output
# to migrate the Dockerfile from floating tags to pinned digests once a
# Renovate (or equivalent) workflow is in place to refresh them on a schedule.
#
# Without an automated refresh, manually-pinned digests go stale and the
# resulting image stops receiving upstream security patches. That is *worse*
# than a floating tag — only adopt the pins when the refresh story is solved.
#
# Requires: bash, curl, jq.
#
# Usage: tools/refresh-base-images.sh [--auth USER:TOKEN]
#
#   --auth USER:TOKEN   Optional Docker Hub credentials for unauthenticated
#                       rate-limit bypass (anonymous limit is 100/6h per IP).

set -euo pipefail

IMAGES=(
  "library/eclipse-temurin:25-jdk-jammy"
  "library/eclipse-temurin:25-jdk-alpine"
  "library/alpine:3.23"
)

AUTH=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --auth) AUTH="$2"; shift 2 ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
done

token_for() {
  local repo="$1"
  local creds="${AUTH:+-u $AUTH}"
  # shellcheck disable=SC2086
  curl -fsSL $creds \
    "https://auth.docker.io/token?service=registry.docker.io&scope=repository:${repo}:pull" \
    | jq -r .token
}

digest_for() {
  local image_with_tag="$1"
  local repo="${image_with_tag%:*}"
  local tag="${image_with_tag##*:}"
  local token
  token="$(token_for "$repo")"
  curl -fsSL -I \
    -H "Accept: application/vnd.oci.image.index.v1+json" \
    -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
    -H "Accept: application/vnd.docker.distribution.manifest.list.v2+json" \
    -H "Authorization: Bearer ${token}" \
    "https://registry-1.docker.io/v2/${repo}/manifests/${tag}" \
    | tr -d '\r' \
    | awk '/^[Dd]ocker-[Cc]ontent-[Dd]igest:/ { print $2 }'
}

echo "# Pin lines — paste the matching FROM ... clauses into Dockerfile."
echo "# Refresh by re-running this script (and update the comment date)."
echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
for image in "${IMAGES[@]}"; do
  digest="$(digest_for "$image")"
  display="${image#library/}"
  printf 'FROM %s@%s\n' "$display" "$digest"
done
