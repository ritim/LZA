#!/usr/bin/env bash
# ============================================================================
# gen-dev-certs.sh
# ----------------------------------------------------------------------------
# 用 mkcert 產生 self-signed CA + cert，給 docker-compose.tls.yml /
# application-tls.yml 使用。
#
# 產出：
#   scripts/dev-certs/aethercare.crt   PEM cert
#   scripts/dev-certs/aethercare.key   PEM private key
#   scripts/dev-certs/truststore.p12   Java PKCS12 truststore（給 Spring）
#   scripts/dev-certs/keystore.p12     Java PKCS12 keystore（給 Kafka / mTLS）
#
# 前置：brew install mkcert（macOS 推薦）。
# ============================================================================
set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")" && pwd)/dev-certs"
mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

if ! command -v mkcert >/dev/null 2>&1; then
  echo "[error] 請先安裝 mkcert：brew install mkcert"
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "[error] 找不到 keytool（需要 JDK 21 在 PATH）"
  exit 1
fi

echo "[1/4] 安裝 mkcert root CA ..."
mkcert -install

echo "[2/4] 簽發 dev cert ..."
mkcert -cert-file aethercare.crt -key-file aethercare.key \
  localhost 127.0.0.1 \
  aethercare-postgres aethercare-redis aethercare-kafka aethercare-vault

echo "[3/4] 匯出 PKCS12 truststore（含 mkcert root CA）..."
rm -f truststore.p12
keytool -import -trustcacerts -alias mkcert-ca \
  -file "$(mkcert -CAROOT)/rootCA.pem" \
  -keystore truststore.p12 -storepass changeit -storetype PKCS12 -noprompt

echo "[4/4] 匯出 PKCS12 keystore（cert+key 給 Kafka SSL listener 用）..."
rm -f keystore.p12
openssl pkcs12 -export \
  -in aethercare.crt -inkey aethercare.key \
  -out keystore.p12 -name aethercare \
  -passout pass:changeit

echo
echo "[done] dev cert 已生成於 $CERT_DIR"
ls -la "$CERT_DIR"
