#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="com.envy.dualcorevpn"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  printf 'VPN_SMOKE_FAIL: %s\n' "$*" >&2
  exit 1
}

snapshot() {
  "$ADB" shell uiautomator dump /sdcard/lust-smoke-ui.xml >/dev/null
  "$ADB" pull /sdcard/lust-smoke-ui.xml "$TMP_DIR/ui.xml" >/dev/null
}

has_text() {
  snapshot
  python3 - "$TMP_DIR/ui.xml" "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET
path, expected = sys.argv[1:]
found = any(node.attrib.get("text") == expected for node in ET.parse(path).getroot().iter("node"))
raise SystemExit(0 if found else 1)
PY
}

wait_for_text() {
  local expected="$1"
  local attempts="${2:-20}"
  for _ in $(seq 1 "$attempts"); do
    if has_text "$expected"; then return 0; fi
    sleep 1
  done
  fail "UI did not reach: $expected"
}

click_text() {
  local expected="$1"
  snapshot
  local point
  point="$(python3 - "$TMP_DIR/ui.xml" "$expected" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
path, expected = sys.argv[1:]
for node in ET.parse(path).getroot().iter("node"):
    if node.attrib.get("text") == expected:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit(1)
PY
)" || fail "UI action not found: $expected"
  # The Compose semantics node itself is not clickable, but its center is inside the button.
  "$ADB" shell input tap $point
}

wait_for_vpn() {
  for _ in $(seq 1 20); do
    if "$ADB" shell dumpsys connectivity | grep -Eq 'VPN CONNECTED.*InterfaceName: tun0'; then return 0; fi
    sleep 1
  done
  fail "Android ConnectivityService did not report VPN CONNECTED on tun0"
}

wait_for_no_tun() {
  for _ in $(seq 1 20); do
    if ! "$ADB" shell ip addr show tun0 >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  fail "tun0 remained after disconnect"
}

"$ADB" get-state >/dev/null || fail "ADB device unavailable"
[[ -f "$APK" ]] || fail "APK not found: $APK"
"$ADB" install -r "$APK" >/dev/null
"$ADB" logcat -c
"$ADB" shell am force-stop "$PACKAGE"
"$ADB" shell monkey -p "$PACKAGE" 1 >/dev/null
wait_for_text "ПОДКЛЮЧИТЬ"

for cycle in 1 2; do
  click_text "ПОДКЛЮЧИТЬ"
  wait_for_text "ЗАЩИЩЕНО"
  wait_for_vpn

  # ICMP is not supported by the SOCKS transport. Verify TCP and DNS instead.
  "$ADB" shell 'printf x | nc -w 8 1.1.1.1 443' >/dev/null || fail "TCP traffic failed in cycle $cycle"
  dns_ok=false
  for _ in $(seq 1 5); do
    dns_output="$("$ADB" shell 'ping -c 1 -W 1 example.com' 2>&1 || true)"
    if grep -Eq '^PING .* \([0-9a-fA-F:.]+\)' <<<"$dns_output"; then
      dns_ok=true
      break
    fi
    sleep 1
  done
  $dns_ok || fail "DNS resolution failed in cycle $cycle"

  click_text "ОТКЛЮЧИТЬ"
  wait_for_text "ПОДКЛЮЧИТЬ"
  wait_for_no_tun
done

fatal_count="$($ADB logcat -d | grep -Ec 'FATAL EXCEPTION|Fatal signal|JNI DETECTED ERROR' || true)"
[[ "$fatal_count" == "0" ]] || fail "fatal Android/JNI events: $fatal_count"

printf 'VPN_SMOKE_PASS cycles=2 tcp=ok dns=ok disconnect=ok fatal=0\n'
