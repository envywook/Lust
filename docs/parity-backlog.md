# Lust Android parity backlog

Baseline: Lust `472d752`, v2RayTun Android 5.24.76, and v2rayN commit `fd2c942231b0593a5ab65c07d677acf0074c34ce`.

Parity means comparable user capability, not copied UI. v2RayTun is the primary Android reference. Desktop-only v2rayN features such as tray controls, OS process routing, hotkeys, system proxy, WebDAV, and executable-core management are excluded unless adapted to a real Android use case.

## P0 — dependable daily use

- [ ] Standalone profile import from clipboard, Android share intent, manual URI, base64 bundle, and Xray JSON, with preview before save.
- [ ] Atomic subscription refresh: preserve old nodes on HTTP, parse, cancellation, or persistence failure; show imported/skipped/error counts and last successful refresh.
- [ ] Explicit protocol coverage matrix with parser fixtures and clear unsupported-format errors.
- [ ] Routing modes: global, bypass LAN/private, and rule-based domain/IP/GeoSite/GeoIP routing.
- [ ] DNS modes with bootstrap/resolver separation, IPv4/IPv6 policy, and leak-oriented tests.
- [ ] Per-app VPN allowlist/blocklist using installed-app selection and `VpnService.Builder` package rules.
- [ ] Observable server latency testing with timeout/error state, bounded concurrency, cancellation, and persisted last result.
- [ ] Profile/subscription/settings backup and restore with schema versioning, preview, conflict policy, and atomic commit.
- [ ] Deterministic emulator E2E: direct local target at `10.0.2.2`, VPN app-op consent, connect/traffic/DNS/disconnect/reconnect/revoke/startup-cancel assertions.
- [ ] Honest runtime selection: expose a core only after its binary/library, config generation, lifecycle, traffic, and failure handling pass acceptance.

## P1 — major parity and operations

- [ ] Subscription groups, enable/disable, ordering, bulk refresh, update interval, and optional user-agent/header configuration.
- [ ] Server search, filtering, sorting, favorites, duplicate detection, rename/edit/delete, and batch operations.
- [ ] Routing-rule import/export and editable custom domain/IP rules.
- [ ] Split tunneling controls for LAN, selected CIDRs, and selected applications.
- [ ] Deep links for add-subscription/import actions with explicit confirmation and safe URL handling.
- [ ] Diagnostics bundle: sanitized config, app/core versions, device/VPN state, logs, and settings without credentials.
- [ ] Automatic subscription refresh through WorkManager with network constraints and actionable notifications.

## P2 — advanced

- [ ] Multiple user-selectable routing and DNS presets.
- [ ] LAN sharing only with explicit bind-address/authentication controls and warnings.
- [ ] Optional remote backup provider based on demonstrated Android demand.
- [ ] Protocol-specific advanced editors after common import and connection flows are stable.

## Explicitly out of Android scope

- Desktop tray and hotkeys.
- Desktop system-proxy toggling.
- Arbitrary desktop process routing.
- Downloading/managing executable cores as desktop files.
- Literal WebDAV parity without an Android user requirement.

## Acceptance rule

A checkbox is complete only when the capability has domain/unit tests, Android integration verification where applicable, failure-path behavior, persistent-state verification, and no regression in `scripts/android-vpn-smoke.sh`.

## Sources

- https://github.com/envywook/Lust/blob/472d752dc31dcee12aac07c31518b5f39a262ca2/README.md
- https://github.com/DigneZzZ/v2raytun/releases/tag/5.24.76
- https://docs.v2raytun.com/
- https://docs.v2raytun.com/overview/supported-headers
- https://docs.v2raytun.com/deep-link
- https://github.com/2dust/v2rayN/blob/fd2c942231b0593a5ab65c07d677acf0074c34ce/README.md
