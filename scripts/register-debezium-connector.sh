#!/usr/bin/env bash
set -euo pipefail
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_FILE="$(dirname "$0")/debezium-outbox-connector.json"

echo "等待 Kafka Connect 就緒..."
until curl -fsS "$CONNECT_URL/" > /dev/null 2>&1; do sleep 2; done

echo "註冊 Debezium Outbox Connector..."
curl -fsS -X POST "$CONNECT_URL/connectors" \
  -H 'Content-Type: application/json' \
  -d @"$CONNECTOR_FILE"
echo ""

echo "Connector 狀態："
curl -fsS "$CONNECT_URL/connectors/aethercare-outbox-connector/status" | python3 -m json.tool || true

echo ""
echo "若需移除：curl -X DELETE $CONNECT_URL/connectors/aethercare-outbox-connector"
