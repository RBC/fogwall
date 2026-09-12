#!/usr/bin/env bash
# Generate a CA + localhost leaf cert for serving fogwall's HTTPS listeners locally over TLS, into docker/tls/:
#   ca.crt        trust anchor — clients pass it via SSL_CERT_FILE
#   localhost.crt cert fogwall serves (leaf + CA fullchain), via FOGWALL_SERVER_TLS_CERTIFICATE
#   localhost.key its key, via FOGWALL_SERVER_TLS_KEY
set -euo pipefail
TLS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker/tls"
mkdir -p "$TLS"; cd "$TLS"
rm -f ca.crt ca.key localhost.crt localhost.key localhost.csr localhost-leaf.crt ca.srl

# CA (trust anchor)
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.crt \
  -subj "/CN=fogwall-local-dev-CA" -days 365 \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
# leaf (CA:FALSE), signed by the CA, with SAN + serverAuth
openssl req -newkey rsa:2048 -nodes -keyout localhost.key -out localhost.csr -subj "/CN=localhost" 2>/dev/null
openssl x509 -req -in localhost.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out localhost-leaf.crt -days 365 \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n") 2>/dev/null
cat localhost-leaf.crt ca.crt > localhost.crt   # fullchain the server presents
chmod 644 localhost.key                          # readable by the container's non-root user
rm -f localhost.csr localhost-leaf.crt ca.srl
echo "wrote $TLS/{ca.crt,ca.key,localhost.crt,localhost.key}"
