#!/usr/bin/env bash
# Generate self-signed CA + server cert for TLS benchmark testing.
# Outputs:
#   ca.pem / ca-key.pem       — CA cert and key
#   server.pem / server-key.pem — server cert and key (signed by CA)
#   truststore.jks             — JKS truststore containing the CA cert (for Java)
#
# Usage:
#   bash perf/tls/generate-certs.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

DAYS=365
CA_SUBJECT="/CN=fogwall-perf-ca"
SERVER_SUBJECT="/CN=localhost"
TRUSTSTORE_PASS="changeit"

# ── CA ────────────────────────────────────────────────────────────────────
echo "==> Generating CA..."
openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout ca-key.pem -out ca.pem \
    -days "${DAYS}" -subj "${CA_SUBJECT}" 2>/dev/null

# ── Server cert ──────────────────────────────────────────────────────────
echo "==> Generating server cert for localhost..."
openssl req -newkey rsa:2048 -nodes \
    -keyout server-key.pem -out server.csr \
    -subj "${SERVER_SUBJECT}" 2>/dev/null

cat > server-ext.cnf <<EOF
subjectAltName = DNS:localhost, DNS:gitea, IP:127.0.0.1
EOF

openssl x509 -req -in server.csr \
    -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
    -out server.pem -days "${DAYS}" \
    -extfile server-ext.cnf 2>/dev/null

rm -f server.csr server-ext.cnf ca.srl

# ── JKS truststore (for Java / fogwall) ─────────────────────────────────
echo "==> Creating JKS truststore..."
rm -f truststore.jks
keytool -importcert -noprompt \
    -alias fogwall-perf-ca \
    -file ca.pem \
    -keystore truststore.jks \
    -storepass "${TRUSTSTORE_PASS}" 2>/dev/null

echo ""
echo "==> Certs generated in ${SCRIPT_DIR}/"
echo "    CA:          ca.pem (+ ca-key.pem)"
echo "    Server:      server.pem (+ server-key.pem)"
echo "    Truststore:  truststore.jks (password: ${TRUSTSTORE_PASS})"
echo ""
echo "    Java:  -Djavax.net.ssl.trustStore=${SCRIPT_DIR}/truststore.jks -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASS}"
echo "    Node:  NODE_EXTRA_CA_CERTS=${SCRIPT_DIR}/ca.pem"
echo "    Git:   GIT_SSL_CAINFO=${SCRIPT_DIR}/ca.pem"
