#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

failures=0

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    printf 'FAIL file %s\n' "$path"
    failures=$((failures + 1))
    return
  fi
  printf 'PASS file %s\n' "$path"
}

require_text() {
  local path="$1"
  local needle="$2"
  local label="$3"
  if grep -Fq "$needle" "$ROOT_DIR/$path"; then
    printf 'PASS %-12s %s\n' "$label" "$path"
  else
    printf 'FAIL %-12s %s missing: %s\n' "$label" "$path" "$needle"
    failures=$((failures + 1))
  fi
}

require_absent() {
  local path="$1"
  local needle="$2"
  local label="$3"
  if grep -Fq "$needle" "$ROOT_DIR/$path"; then
    printf 'FAIL %-12s %s contains forbidden: %s\n' "$label" "$path" "$needle"
    failures=$((failures + 1))
  else
    printf 'PASS %-12s %s\n' "$label" "$path"
  fi
}

printf 'Android no motion config matrix smoke\n'
printf 'mode: read-only static checks, no APK install, no pm clear, no device, no robot\n\n'

require_file "app/build.gradle.kts"
require_file "build_rustore_release.sh"
require_file "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt"
require_file "app/src/main/java/com/maxcorp/edgeconnector/MainActivity.kt"
require_file "app/src/main/java/com/maxcorp/edgeconnector/PrivacyPolicy.kt"
require_file "app/src/test/java/com/maxcorp/edgeconnector/ConfigStoreTest.kt"
require_file "app/src/test/java/com/maxcorp/edgeconnector/ConnectorConfigTest.kt"
require_file "app/src/test/java/com/maxcorp/edgeconnector/LegalConfigTest.kt"
require_file ".github/workflows/android-ci.yml"

printf '\n'
printf 'row runtime: panel URL comes from GOSHA_PANEL_BASE_URL or activation bundle, not a hardcoded live endpoint\n'
require_text "app/build.gradle.kts" "val runtimePanelBaseUrl = configValue(\"GOSHA_PANEL_BASE_URL\")" "runtime"
require_text "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "internal fun runtimeDefaultPanelBaseUrl(): String = BuildConfig.DEFAULT_PANEL_BASE_URL.trim()" "runtime"
require_text "app/src/main/java/com/maxcorp/edgeconnector/MainActivity.kt" "if (!isHttpUrl(baseUrl)) {" "runtime"
require_text "app/src/main/java/com/maxcorp/edgeconnector/MainActivity.kt" "PanelApiClient.activateCode(httpClient, baseUrl, code, ownerName, ownerEmail, ownerPhone)" "runtime"

printf '\n'
printf 'row saved: saved binding survives URL migration, while fresh install stays blank and fail-closed\n'
require_text "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "private const val K_PANEL_URL = \"panel_url\"" "saved"
require_text "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "private const val K_CLOUD_ENDPOINT = \"cloud_endpoint\"" "saved"
require_text "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "private const val K_CONNECTOR_ROBOT_HOST = \"connector_robot_host\"" "saved"
require_text "app/src/test/java/com/maxcorp/edgeconnector/ConfigStoreTest.kt" "fresh install does not invent public panel endpoint or connector identity" "saved"
require_text "app/src/test/java/com/maxcorp/edgeconnector/ConnectorConfigTest.kt" "runtime url migration preserves token and device identity without relay literals" "saved"

printf '\n'
printf 'row release: release build and RuStore script require explicit http(s) panel and legal URLs\n'
require_text "app/build.gradle.kts" "\"GOSHA_PANEL_BASE_URL\" to runtimePanelBaseUrl" "release"
require_text "app/build.gradle.kts" "\"RUSTORE_PRIVACY_POLICY_URL\" to rustorePrivacyPolicyUrl" "release"
require_text "app/build.gradle.kts" "\"RUSTORE_TERMS_OF_USE_URL\" to rustoreTermsOfUseUrl" "release"
require_text "app/build.gradle.kts" "tasks.register(\"verifyReleaseRuntimeConfig\")" "release"
require_text "build_rustore_release.sh" "GOSHA_PANEL_BASE_URL" "release"
require_text "build_rustore_release.sh" "RUSTORE_PRIVACY_POLICY_URL" "release"
require_text "build_rustore_release.sh" "RUSTORE_TERMS_OF_USE_URL" "release"

printf '\n'
printf 'row ci: Draft PR CI is read-only, debug-only, and records a debug APK digest without production signing\n'
require_text ".github/workflows/android-ci.yml" "pull_request:" "ci"
require_text ".github/workflows/android-ci.yml" "contents: read" "ci"
require_text ".github/workflows/android-ci.yml" "java-version: \"17\"" "ci"
require_text ".github/workflows/android-ci.yml" "platforms;android-34" "ci"
require_text ".github/workflows/android-ci.yml" "testClientDebugUnitTest assembleClientDebug lintClientDebug" "ci"
require_text ".github/workflows/android-ci.yml" "scripts/verify_android_no_motion_config_matrix.sh" "ci"
require_text ".github/workflows/android-ci.yml" "https://panel.example.invalid" "ci"
require_text ".github/workflows/android-ci.yml" "sha256sum" "ci"
require_absent ".github/workflows/android-ci.yml" "secrets." "ci"
require_absent ".github/workflows/android-ci.yml" "assembleClientRelease" "ci"
require_absent ".github/workflows/android-ci.yml" "adb" "ci"

printf '\n'
printf 'row legal: legal documents are opened only when configured as explicit http(s) URLs\n'
require_text "app/src/main/java/com/maxcorp/edgeconnector/PrivacyPolicy.kt" "fun isConfigured(): Boolean = isHttpUrl(url())" "legal"
require_text "app/src/main/java/com/maxcorp/edgeconnector/PrivacyPolicy.kt" "object TermsOfUse" "legal"
require_text "app/src/test/java/com/maxcorp/edgeconnector/LegalConfigTest.kt" "release legal url policy rejects missing and invalid values" "legal"
require_text "app/src/test/java/com/maxcorp/edgeconnector/LegalConfigTest.kt" "debug defaults leave legal documents unconfigured without release config" "legal"

printf '\n'
printf 'row forbidden: Android product/test release matrix must not pin legacy endpoints\n'
require_absent "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "TEMP_NL_RELAY" "forbidden"
require_absent "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "8876" "forbidden"
require_absent "app/src/main/java/com/maxcorp/edgeconnector/ConnectorConfig.kt" "8890" "forbidden"
require_absent "app/build.gradle.kts" "TEMP_NL_RELAY" "forbidden"

printf '\n'
if [[ "$failures" -ne 0 ]]; then
  printf 'FAIL android no motion config matrix smoke: %d checks failed; see the exact FAIL rows above\n' "$failures"
  exit 1
fi

printf 'PASS android no motion config matrix smoke\n'
