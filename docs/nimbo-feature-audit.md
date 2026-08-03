# Nimbo feature audit for MaxSpeedVPN

Audit date: 2026-07-24

Source: <https://github.com/BBGGVP5/nimbo>

Inspected commit: `307a6c42cbad56aae5fd1dcf9fef9cd488ff4a1a`

## Licensing decision

No `LICENSE` or `COPYING` file exists in the inspected Git tree. Copyright therefore remains reserved by default. MaxSpeedVPN must not copy Nimbo source, resources, branding, or screen implementations. Features are implemented independently from public behavior and standard formats, using MaxSpeedVPN's architecture, naming, tests, and UI.

## Feature map

| Nimbo area/evidence | Android value for Lust | Lust status/approach |
|---|---|---|
| `ProfilesScreen`, `ProfileServersScreen`, `SubscriptionSettingsScreen` | subscriptions, profile/server organization | Implemented independently: merged home, groups, search, favorites, sort, atomic refresh |
| `SubscriptionRequestIdentity`, subscription model | provider requests and subscription metadata | Implemented: safe deep links, clipboard confirmation, `subscription-userinfo` traffic/limit/expiry |
| `PingSettingsScreen`, `ConnectivityDiagnosticsScreen` | endpoint diagnostics | Partly implemented: bounded TCP endpoint latency; must not be labelled proxy delay |
| `BackupScreen` | portable configuration | Implemented: versioned JSON, SAF export/import, pre-validation, rollback, sensitive-token warning |
| `RoutingScreen`, `AppProxySettingsScreen` | split tunneling/per-app routing | Planned; requires VPN builder and core rule parity tests |
| `TrafficMonitorScreen` | live session traffic | Planned; requires trustworthy HEV/core counters rather than fabricated estimates |
| `UpdateScreen`, `UpdateManager` | in-app update/changelog | Planned; GitHub release notes and project changelog already enforced in CI |
| `BootRestoreReceiver` | reconnect after reboot | Planned, opt-in only; must respect Android background restrictions |
| `NotificationHistoryScreen` | operational history | Existing Lust persistent diagnostic log covers core/service/UI events |
| `NetworkPresetsScreen`, `NetworkProfileManager` | network-aware profiles | Planned after lifecycle/network callback tests |
| `AppearanceSettingsScreen`, `AppIconSettingsScreen` | appearance customization | Low priority; current redesign follows Lust design system |
| Hysteria2/XHTTP/HTTPUpgrade handling in core/config layers | protocol/transport parity | Planned through sing-box schema and real Android traffic acceptance |
| QR workflows | fast import/share | Planned with a small, audited decoder; avoid a large ML dependency that defeats APK reduction |
| Quick settings/service controls | fast connection toggle | Planned after foreground-service lifecycle acceptance |

## Explicitly excluded

- Desktop-only system proxy/Tauri behavior.
- Blind copying of Nimbo UI or Kotlin/Rust files.
- Any feature whose UI claims stronger measurement than its implementation.
- Automatic network fetch from an external deep link without user confirmation.

## Release requirements

Each release must include:

1. explicit release notes and updated `CHANGELOG.md`;
2. updated README/GitHub project page when capabilities or downloads change;
3. universal plus `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86` APKs;
4. SHA-256 checksums;
5. unit, build, lint, Android device smoke, and both Xray/sing-box regression gates.
