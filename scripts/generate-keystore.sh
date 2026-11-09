#!/usr/bin/env bash
#
# Creates the release signing key that every published APK must share.
# Without a stable key, Android refuses to install an update over the existing app.
#
# Usage: ./scripts/generate-keystore.sh [path-to-keystore]
#
set -euo pipefail

KEYSTORE="${1:-release.jks}"
ALIAS="${KEY_ALIAS:-trolley}"
PASSWORD="${KEYSTORE_PASSWORD:-$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)}"
DN="${KEY_DNAME:-CN=Trolley, OU=Mobile, O=Trolley, C=GB}"

if [[ -f "$KEYSTORE" ]]; then
  echo "Refusing to overwrite $KEYSTORE — move it aside first." >&2
  exit 1
fi

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "$DN"

cat > keystore.properties <<EOF
KEYSTORE_PATH=$KEYSTORE
KEYSTORE_PASSWORD=$PASSWORD
KEY_ALIAS=$ALIAS
KEY_PASSWORD=$PASSWORD
EOF

echo
echo "Created $KEYSTORE and keystore.properties (both git-ignored)."
echo "Back the keystore up: it is the only way to keep in-app updates working."
echo
echo "Add these four repository secrets (Settings -> Secrets and variables -> Actions):"
echo
echo "  KEYSTORE_BASE64"
echo "  KEYSTORE_PASSWORD  = $PASSWORD"
echo "  KEY_ALIAS          = $ALIAS"
echo "  KEY_PASSWORD       = $PASSWORD"
echo
echo "KEYSTORE_BASE64 value:"
echo
base64 < "$KEYSTORE" | tr -d '\n'
echo
