#!/usr/bin/env bash
#
# Liquibase rollback CLI（用 liquibase/liquibase Docker image，不依賴 gradle plugin）
#
# Usage:
#   ./scripts/liquibase-rollback.sh count <N>           # rollback 最近 N 個 changeset
#   ./scripts/liquibase-rollback.sh tag <tagName>       # rollback 到指定 tag
#   ./scripts/liquibase-rollback.sh status              # 查目前 status
#   ./scripts/liquibase-rollback.sh history             # 查已 applied changeset 歷史
#   ./scripts/liquibase-rollback.sh tag-now <tagName>   # 對當前 DB 加 tag（給未來 rollback 用）
#
# Env vars（必填或用預設）：
#   AETHERCARE_DB_URL      jdbc:postgresql://host:port/db (預設 jdbc:postgresql://localhost:15432/aethercare)
#   AETHERCARE_DB_USER     (預設 aethercare)
#   AETHERCARE_DB_PASSWORD (預設 aethercare)
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHANGELOG_DIR="${REPO_ROOT}/aethercare-api/src/main/resources/db/changelog"

DB_URL="${AETHERCARE_DB_URL:-jdbc:postgresql://localhost:15432/aethercare}"
DB_USER="${AETHERCARE_DB_USER:-aethercare}"
DB_PASSWORD="${AETHERCARE_DB_PASSWORD:-aethercare}"
LIQUIBASE_IMAGE="liquibase/liquibase:4.31"

cmd="${1:-}"
shift || true

# JDBC URL 在 docker container 內必須改用 host.docker.internal 或同 network
# 這裡假設 docker compose 已起 PG 在 aethercare-postgres，所以用 host.docker.internal
DOCKER_DB_URL=$(echo "$DB_URL" | sed 's/localhost/host.docker.internal/')

run_liquibase() {
    docker run --rm \
        -v "${CHANGELOG_DIR}:/liquibase/changelog" \
        --add-host=host.docker.internal:host-gateway \
        "${LIQUIBASE_IMAGE}" \
        --changeLogFile=db.changelog-master.yaml \
        --url="${DOCKER_DB_URL}" \
        --username="${DB_USER}" \
        --password="${DB_PASSWORD}" \
        "$@"
}

case "$cmd" in
    count)
        n="${1:-1}"
        echo "===> Rolling back last $n changeset(s)..."
        run_liquibase rollbackCount "$n"
        ;;
    tag)
        tag="${1:?需指定 tag 名稱}"
        echo "===> Rolling back to tag: $tag"
        run_liquibase rollback "$tag"
        ;;
    status)
        run_liquibase status --verbose
        ;;
    history)
        run_liquibase history
        ;;
    tag-now)
        tag="${1:?需指定 tag 名稱}"
        echo "===> 對當前 DB 加 tag: $tag"
        run_liquibase tag "$tag"
        ;;
    *)
        echo "Usage: $0 {count <N> | tag <name> | status | history | tag-now <name>}"
        exit 1
        ;;
esac
