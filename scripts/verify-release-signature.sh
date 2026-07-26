#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_SHA256="5c9fb76e8a42eb4fecba7206fa20f35f54c78585d416b233ea77fcfbd343add6"
readonly APKSIGNER="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/34.0.0/apksigner"

(($# > 0)) || { echo "Usage: $0 APK..." >&2; exit 2; }
for apk in "$@"; do
  output=$("${APKSIGNER}" verify --verbose --print-certs "${apk}")
  grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"${output}"
  actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"${output}")
  [[ "${actual}" == "${EXPECTED_SHA256}" ]] || {
    echo "Unexpected signing certificate for ${apk}" >&2
    exit 1
  }
done
