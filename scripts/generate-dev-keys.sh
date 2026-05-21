#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
KEYS_DIR="${REPO_ROOT}/keys"

ALIAS="${ALIAS:-oauth2-client}"
PASSWORD="${PASSWORD:-changeit}"
DNAME="${DNAME:-CN=oauth2-jwt-client, O=Odea Integrations, C=US}"
DAYS="${DAYS:-365}"

mkdir -p "${KEYS_DIR}"

# Locate keytool: prefer PATH, then JAVA_HOME, then common JDK install dirs.
KEYTOOL="${KEYTOOL:-}"
if [ -z "${KEYTOOL}" ] && command -v keytool >/dev/null 2>&1; then
    KEYTOOL="keytool"
fi
if [ -z "${KEYTOOL}" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ]; then
    KEYTOOL="${JAVA_HOME}/bin/keytool"
fi
if [ -z "${KEYTOOL}" ]; then
    for base in \
        "${HOME}/.jdks" \
        "/c/Program Files/Eclipse Adoptium" \
        "/c/Program Files/Java" \
        "/c/Program Files/JetBrains" \
        "/usr/lib/jvm" \
        "/Library/Java/JavaVirtualMachines"; do
        if [ -d "$base" ]; then
            found=$(find "$base" -name keytool -o -name keytool.exe 2>/dev/null | head -n 1)
            if [ -n "$found" ]; then KEYTOOL="$found"; break; fi
        fi
    done
fi
if [ -z "${KEYTOOL}" ]; then
    echo "ERROR: keytool not found. Install a JDK 1.8 or set KEYTOOL=<full path>." >&2
    exit 1
fi
echo "Using keytool: ${KEYTOOL}"

echo "Generating RSA private key + self-signed cert (PKCS12 keystore)..."
"${KEYTOOL}" -genkeypair -keyalg RSA -keysize 2048 -alias "${ALIAS}" \
    -keystore "${KEYS_DIR}/oauth2-client.p12" -storetype PKCS12 \
    -storepass "${PASSWORD}" -keypass "${PASSWORD}" \
    -dname "${DNAME}" -validity "${DAYS}"

echo "Exporting public certificate (PEM)..."
"${KEYTOOL}" -exportcert -alias "${ALIAS}" \
    -keystore "${KEYS_DIR}/oauth2-client.p12" -storetype PKCS12 \
    -storepass "${PASSWORD}" -rfc \
    -file "${KEYS_DIR}/oauth2-client.crt"

echo
echo "Created files:"
echo "  ${KEYS_DIR}/oauth2-client.p12   (PKCS12 keystore loaded by the app)"
echo "  ${KEYS_DIR}/oauth2-client.crt   (public certificate, give to AS admin)"
echo
echo "Environment variables to set:"
echo "  OAUTH2_KEYSTORE_PATH=${KEYS_DIR}/oauth2-client.p12"
echo "  OAUTH2_KEYSTORE_PASSWORD=${PASSWORD}"
echo "  OAUTH2_KEY_ALIAS=${ALIAS}"
echo "  OAUTH2_KEY_PASSWORD=${PASSWORD}"
