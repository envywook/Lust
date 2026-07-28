#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="com.envy.dualcorevpn"
APK="${1:-}"
TMP_DIR="$(mktemp -d)"

stop_runtime() {
  "$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  "$ADB" shell 'pids=$(pidof libsingbox.so); [ -z "$pids" ] || kill $pids' >/dev/null 2>&1 || true
}

cleanup() {
  stop_runtime
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  printf 'VPN_SMOKE_FAIL: %s\n' "$*" >&2
  snapshot >/dev/null 2>&1 || true
  printf '%s\n' '--- UI hierarchy ---' >&2
  sed -n '1,120p' "$TMP_DIR/ui.xml" >&2 2>/dev/null || true
  printf '%s\n' '--- foreground activity ---' >&2
  "$ADB" shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
  "$ADB" shell dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' >&2 || true
  printf '%s\n' '--- application logcat ---' >&2
  "$ADB" logcat -d -t 300 2>/dev/null | grep -E "${PACKAGE}|AndroidRuntime|ActivityTaskManager|WindowManager" >&2 || true
  exit 1
}

snapshot() {
  "$ADB" shell uiautomator dump /sdcard/lust-smoke-ui.xml >/dev/null
  "$ADB" pull /sdcard/lust-smoke-ui.xml "$TMP_DIR/ui.xml" >/dev/null
}

has_ui_label() {
  snapshot
  python3 - "$TMP_DIR/ui.xml" "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET
path, expected = sys.argv[1:]
found = any(
    node.attrib.get("text") == expected or node.attrib.get("content-desc") == expected
    for node in ET.parse(path).getroot().iter("node")
)
raise SystemExit(0 if found else 1)
PY
}

wait_for_ui_label() {
  local expected="$1"
  local attempts="${2:-20}"
  for _ in $(seq 1 "$attempts"); do
    if has_ui_label "$expected"; then return 0; fi
    sleep 1
  done
  fail "UI did not reach label: $expected"
}

start_app() {
  "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  for attempt in 1 2 3; do
    "$ADB" shell am force-stop "$PACKAGE"
    if "$ADB" shell am start -W -n "$PACKAGE/.MainActivity"; then
      for _ in 1 2 3 4 5; do
        if "$ADB" shell dumpsys activity activities 2>/dev/null | grep -Eq "(topResumedActivity|mResumedActivity).*$PACKAGE"; then
          return 0
        fi
        sleep 1
      done
    fi
    echo "Activity start attempt $attempt did not reach foreground" >&2
    sleep $((attempt * 2))
  done
  fail "MainActivity did not reach foreground"
}

click_ui_label() {
  local expected="$1"
  snapshot
  local point
  point="$(python3 - "$TMP_DIR/ui.xml" "$expected" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
path, expected = sys.argv[1:]
for node in ET.parse(path).getroot().iter("node"):
    if node.attrib.get("text") == expected or node.attrib.get("content-desc") == expected:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit(1)
PY
)" || fail "UI action not found: $expected"
  # A merged Compose semantics node may not be clickable itself, but its center is inside the button.
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
if [[ -z "$APK" ]]; then
  device_abi="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
  APK="app/build/outputs/apk/debug/app-${device_abi}-debug.apk"
fi
[[ -f "$APK" ]] || fail "APK not found for device ABI: $APK"
stop_runtime
if [[ "${SKIP_APK_INSTALL:-0}" != "1" ]]; then
  "$ADB" install -r --no-streaming "$APK" >/dev/null
fi
"$ADB" logcat -c
"$ADB" shell am force-stop "$PACKAGE"
start_app
if has_ui_label "Tap to connect"; then
  connect_label="Tap to connect"
  connected_label="Connected"
  disconnect_label="Tap to disconnect"
else
  connect_label="Нажмите, чтобы подключиться"
  connected_label="Подключено"
  disconnect_label="Нажмите, чтобы отключиться"
  wait_for_ui_label "$connect_label"
fi

expected_engine="${EXPECT_ENGINE:-XRAY}"
settings_xml="$("$ADB" exec-out run-as "$PACKAGE" cat shared_prefs/vpn_settings.xml 2>/dev/null || true)"
configured_engine="$(grep -oE '<string name="engine">[^<]+' <<<"$settings_xml" | sed 's/.*>//' | tr -d '\r' || true)"
configured_engine="${configured_engine:-XRAY}"
[[ "$configured_engine" == "$expected_engine" ]] || fail "configured engine is $configured_engine, expected $expected_engine"

for cycle in 1 2; do
  click_ui_label "$connect_label"
  wait_for_ui_label "$connected_label"
  wait_for_vpn

  runtime_log="$("$ADB" exec-out run-as "$PACKAGE" cat files/logs/lust.log 2>/dev/null || true)"
  if [[ "$expected_engine" == "SING_BOX" ]]; then
    "$ADB" shell pidof libsingbox.so >/dev/null || fail "sing-box subprocess is not running in cycle $cycle"
    grep -q 'SING_BOX.*READY' <<<"$runtime_log" || fail "sing-box READY evidence missing in cycle $cycle"
  else
    "$ADB" shell pidof libsingbox.so >/dev/null 2>&1 && fail "sing-box subprocess is running during Xray cycle $cycle"
    grep -q 'Starting XRAY + HEV session' <<<"$runtime_log" || fail "Xray startup evidence missing in cycle $cycle"
  fi

  # ICMP is not supported by the SOCKS transport. Verify TCP and DNS instead.
  "$ADB" shell 'nc -w 8 1.1.1.1 443 </dev/null' >/dev/null || fail "TCP traffic failed in cycle $cycle"
  if [[ "${CHECK_DNS:-1}" == "1" ]]; then
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
  fi

  click_ui_label "$disconnect_label"
  wait_for_no_tun
  start_app
  wait_for_ui_label "$connect_label"
  if [[ "${EXPECT_ENGINE:-XRAY}" == "SING_BOX" ]] && "$ADB" shell pidof libsingbox.so >/dev/null 2>&1; then
    fail "sing-box subprocess remained after disconnect in cycle $cycle"
  fi
done

fatal_count="$($ADB logcat -d | grep -Ec 'FATAL EXCEPTION|Fatal signal|JNI DETECTED ERROR' || true)"
[[ "$fatal_count" == "0" ]] || fail "fatal Android/JNI events: $fatal_count"

dns_result="ok"
[[ "${CHECK_DNS:-1}" == "1" ]] || dns_result="skipped"
printf 'VPN_SMOKE_PASS cycles=2 tcp=ok dns=%s disconnect=ok fatal=0\n' "$dns_result"
