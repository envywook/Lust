#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
ENGINE="${1:?usage: seed-android-smoke.sh XRAY|SING_BOX [apk]}"
APK="${2:-app/build/outputs/apk/debug/app-x86_64-debug.apk}"
PACKAGE="com.envy.dualcorevpn"
[[ "$ENGINE" == "XRAY" || "$ENGINE" == "SING_BOX" ]] || { echo "unsupported engine: $ENGINE" >&2; exit 2; }
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 2; }

"$ADB" wait-for-device
installed=false
for attempt in 1 2 3; do
  if "$ADB" install -r --no-streaming "$APK" >/dev/null; then
    installed=true
    break
  fi
  echo "APK install attempt $attempt failed; retrying" >&2
  "$ADB" wait-for-device
  sleep $((attempt * 5))
done
$installed || { echo "APK installation failed after 3 attempts" >&2; exit 1; }
"$ADB" shell appops set "$PACKAGE" ACTIVATE_VPN allow
"$ADB" shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null
for _ in $(seq 1 20); do
  "$ADB" shell run-as "$PACKAGE" test -d shared_prefs 2>/dev/null && break
  sleep 1
done
"$ADB" shell run-as "$PACKAGE" test -d shared_prefs || { echo "app sandbox was not initialized" >&2; exit 1; }
"$ADB" shell am force-stop "$PACKAGE"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
python3 - "$TMP_DIR" "$ENGINE" <<'PY'
import html,json,pathlib,sys
out=pathlib.Path(sys.argv[1]); engine=sys.argv[2]
config={
 "log":{"loglevel":"warning"},
 "inbounds":[{"tag":"socks-in","listen":"127.0.0.1","port":10808,"protocol":"socks","settings":{"udp":True}}],
 "outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}],
}
subscriptions=[{"id":"smoke-sub","name":"Local smoke","url":"https://localhost.invalid/smoke","updatedAt":1}]
servers=[{"id":"smoke-direct","subscriptionId":"smoke-sub","name":"Local direct smoke","protocol":"freedom","address":"127.0.0.1","port":1,"config":json.dumps(config,separators=(',',':'))}]
def esc(value): return html.escape(value,quote=True)
(out/'subscriptions.xml').write_text("""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="subscriptions">%s</string>
<string name="servers">%s</string>
<string name="selected_server">smoke-direct</string>
<string name="favorite_servers">smoke-direct</string>
</map>
"""%(esc(json.dumps(subscriptions,separators=(',',':'))),esc(json.dumps(servers,separators=(',',':')))))
(out/'vpn_settings.xml').write_text("""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<int name="mtu" value="1500" />
<string name="dns_server">1.1.1.1</string>
<boolean name="ipv6_enabled" value="false" />
<string name="engine">%s</string>
<string name="routing_mode">BYPASS_LAN</string>
<string name="routing_rules"></string>
</map>
"""%engine)
PY
for name in subscriptions vpn_settings; do
  "$ADB" push "$TMP_DIR/$name.xml" "/data/local/tmp/$name.xml" >/dev/null
  "$ADB" shell run-as "$PACKAGE" cp "/data/local/tmp/$name.xml" "shared_prefs/$name.xml"
  "$ADB" shell rm -f "/data/local/tmp/$name.xml"
done
"$ADB" shell am force-stop "$PACKAGE"
printf 'ANDROID_SMOKE_FIXTURE_READY engine=%s\n' "$ENGINE"
