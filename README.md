# Lust

Open-source Android VPN client built with Kotlin and Jetpack Compose. Lust downloads subscription URLs, parses supported proxy links, lets the user select a server, and runs it through an Android `VpnService` backed by Xray-core and HEV tun2socks.

> **Development status:** early alpha. The project builds, but it has not completed production security review or broad real-device interoperability testing. Do not treat it as an audited privacy product.

## Features

- Dark Material 3 interface
- Subscription URL management
- Base64 and plain-text subscription lists
- VLESS, VMess, Trojan, and Shadowsocks link parsing
- Persistent subscription/server selection
- Android `VpnService` integration
- Xray-core via `AndroidLibXrayLite`
- HEV tun2socks for TUN-to-SOCKS transport
- Adaptive launcher icon

## Requirements

- JDK 17
- Android SDK 34
- Android NDK `28.2.13676358`
- `curl`, `unzip`, `readelf`, and `strings`

The Gradle wrapper uses Gradle 8.5. Android Gradle Plugin is 8.2.2; Kotlin is 1.9.22.

## Native dependencies

Large binary dependencies are intentionally excluded from Git. Download the pinned artifacts and verify their published SHA-256 hashes:

```bash
./scripts/prepare-native-deps.sh
```

The script prepares:

- `AndroidLibXrayLite v26.7.19`
- HEV tun2socks libraries extracted from official v2rayNG `2.2.6` ABI builds

Review `THIRD_PARTY_NOTICES.md` before redistributing binaries.

## Build

```bash
./scripts/prepare-native-deps.sh
./gradlew assembleDebug
```

The universal debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew testDebugUnitTest lintDebug
```

Unit-test compilation has been unstable in the current constrained build environment. A successful APK build does not imply that all tests passed.

## Security and privacy

- Subscription URLs and generated proxy configurations may contain credentials.
- Do not commit subscriptions, generated configs, keystores, tokens, or `local.properties`.
- The current alpha stores subscription data in app-private `SharedPreferences`; Android backup is disabled.
- Only import subscriptions from providers you trust.

See [SECURITY.md](SECURITY.md) for vulnerability reporting.

## License

Lust source code is licensed under the [GNU General Public License v3.0](LICENSE).
Third-party components retain their own licenses, including LGPL-3.0 and MIT terms described in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
