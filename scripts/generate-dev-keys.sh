#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
KEYS_DIR="${REPO_ROOT}/keys"

ALIAS="${ALIAS:-oauth2-client}"
PASSWORD="${PASSWORD:-changeit}"
SUBJECT="${SUBJECT:-/CN=oauth2-jwt-client/O=Odea Integrations/C=US}"
DAYS="${DAYS:-365}"

mkdir -p "${KEYS_DIR}"
cd "${KEYS_DIR}"

# Locate openssl: prefer PATH, fall back to common Windows install dir for Git Bash users.
OPENSSL_BIN="${OPENSSL_BIN:-}"
if [ -z "${OPENSSL_BIN}" ]; then
    if command -v openssl >/dev/null 2>&1; then
        OPENSSL_BIN="openssl"
    elif [ -x "/c/Program Files/OpenSSL-Win64/bin/openssl.exe" ]; then
        OPENSSL_BIN="/c/Program Files/OpenSSL-Win64/bin/openssl.exe"
    else
        echo "ERROR: openssl not found on PATH or at /c/Program Files/OpenSSL-Win64/bin/openssl.exe" >&2
        echo "Install OpenSSL or set OPENSSL_BIN=<full path> and retry." >&2
        exit 1
    fi
fi
echo "Using OpenSSL: ${OPENSSL_BIN}"

echo "Generating RSA private key (2048-bit)..."
"${OPENSSL_BIN}" genrsa -out oauth2-client.key 2048

echo "Generating CSR..."
"${OPENSSL_BIN}" req -new -key oauth2-client.key -out oauth2-client.csr -subj "${SUBJECT}"

echo "Self-signing certificate (${DAYS} days)..."
"${OPENSSL_BIN}" x509 -req -days "${DAYS}" -in oauth2-client.csr -signkey oauth2-client.key -out oauth2-client.crt

echo "Building PKCS12 keystore..."
"${OPENSSL_BIN}" pkcs12 -export \
    -in oauth2-client.crt \
    -inkey oauth2-client.key \
    -name "${ALIAS}" \
    -out oauth2-client.p12 \
    -password "pass:${PASSWORD}"

echo
echo "Created files:"
echo "  ${KEYS_DIR}/oauth2-client.key   (private key, do not share)"
echo "  ${KEYS_DIR}/oauth2-client.csr   (certificate request)"
echo "  ${KEYS_DIR}/oauth2-client.crt   (public certificate, give to AS admin)"
echo "  ${KEYS_DIR}/oauth2-client.p12   (PKCS12 keystore loaded by the app)"
echo
echo "Environment variables to set:"
echo "  OAUTH2_KEYSTORE_PATH=${KEYS_DIR}/oauth2-client.p12"
echo "  OAUTH2_KEYSTORE_PASSWORD=${PASSWORD}"
echo "  OAUTH2_KEY_ALIAS=${ALIAS}"
echo "  OAUTH2_KEY_PASSWORD=${PASSWORD}"
