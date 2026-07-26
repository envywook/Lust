#!/usr/bin/env bash
set -euo pipefail

: "${KEYSTORE_BASE64:?LUST_KEYSTORE_BASE64 is required}"
: "${KEYSTORE_PASSWORD:?LUST_KEYSTORE_PASSWORD is required}"
: "${KEY_ALIAS:?LUST_KEY_ALIAS is required}"
: "${KEY_PASSWORD:?LUST_KEY_PASSWORD is required}"
: "${GITHUB_ENV:?GITHUB_ENV is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

keystore="${RUNNER_TEMP}/lust-release.jks"
printf '%s' "${KEYSTORE_BASE64}" | base64 --decode > "${keystore}"
chmod 600 "${keystore}"
{
  echo "LUST_KEYSTORE_PATH=${keystore}"
  echo "LUST_KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD}"
  echo "LUST_KEY_ALIAS=${KEY_ALIAS}"
  echo "LUST_KEY_PASSWORD=${KEY_PASSWORD}"
} >> "${GITHUB_ENV}"
