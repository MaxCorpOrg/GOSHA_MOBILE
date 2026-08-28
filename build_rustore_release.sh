#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME_DEFAULT="/usr/lib/jvm/java-17-openjdk-amd64"
KEYSTORE_FILE="$ROOT_DIR/keystore.properties"

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -f "$KEYSTORE_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$KEYSTORE_FILE"
  set +a
fi

required_vars=(
  GOSHA_PANEL_BASE_URL
  RUSTORE_KEYSTORE_FILE
  RUSTORE_KEYSTORE_PASSWORD
  RUSTORE_KEY_ALIAS
  RUSTORE_KEY_PASSWORD
  RUSTORE_PRIVACY_POLICY_URL
  RUSTORE_TERMS_OF_USE_URL
)

for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing $name. Fill $KEYSTORE_FILE or export the variable before running."
    exit 1
  fi
done

required_url_vars=(
  GOSHA_PANEL_BASE_URL
  RUSTORE_PRIVACY_POLICY_URL
  RUSTORE_TERMS_OF_USE_URL
)

for name in "${required_url_vars[@]}"; do
  value="${!name:-}"
  if [[ ! "$value" =~ ^https?://[^/[:space:]]+(/[^[:space:]]*)?$ ]]; then
    echo "$name must be a valid http(s) URL. Fill $KEYSTORE_FILE or export the variable before running."
    exit 1
  fi
done

"$ROOT_DIR/gradlew" --no-daemon clean assembleClientRelease

APK_PATH="$ROOT_DIR/app/build/outputs/apk/client/release/app-client-release.apk"
if [[ -f "$APK_PATH" ]]; then
  echo "RuStore release APK: $APK_PATH"
else
  echo "Build finished, but the expected APK was not found at $APK_PATH"
  exit 1
fi
