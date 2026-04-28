#!/usr/bin/env bash
# ============================================================================
# seed-vault-secrets.sh
# ----------------------------------------------------------------------------
# 對 docker-compose 啟動的 dev Vault 寫入 demo secrets。
# 對應 application-vault.yml 預期的 key 結構。
#
# 用法：
#   docker compose up -d vault
#   ./scripts/seed-vault-secrets.sh
#
# 前置：本機需安裝 vault CLI（brew install vault），或 docker exec 進 container。
# ============================================================================
set -euo pipefail

export VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:-dev-only-root-token}"

if ! command -v vault >/dev/null 2>&1; then
  echo "[warn] 本機未安裝 vault CLI，改用 docker exec aethercare-vault"
  VAULT_CMD=(docker exec -e "VAULT_ADDR=${VAULT_ADDR}" -e "VAULT_TOKEN=${VAULT_TOKEN}" aethercare-vault vault)
else
  VAULT_CMD=(vault)
fi

echo "[seed] 寫入 secret/aethercare ..."
"${VAULT_CMD[@]}" kv put secret/aethercare \
  spring.datasource.password=aethercare \
  aethercare.security.jwt.secret=Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tcGxlYXNlLW9yLXlvdS1nZXQtaGFja2VkLTIwMjY= \
  aethercare.kafka.sasl.password=demo-kafka-pwd

echo
echo "[done] dev secret 已寫入 secret/aethercare ："
"${VAULT_CMD[@]}" kv get secret/aethercare
