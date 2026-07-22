# Third-party notices

Lust uses or prepares the following third-party components. Their licenses apply independently of Lust's GPL-3.0 license.

## AndroidLibXrayLite

- Project: https://github.com/2dust/AndroidLibXrayLite
- Pinned release: `v26.7.19`
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- Artifact: `app/libs/libv2ray.aar` (downloaded by `scripts/prepare-native-deps.sh`, not committed)

When distributing an APK containing this library, comply with LGPL-3.0 requirements and provide the corresponding source/license information required by that license.

## HEV socks5 tunnel

- Project: https://github.com/heiher/hev-socks5-tunnel
- License: MIT
- Artifacts: `libhev-socks5-tunnel.so` for supported Android ABIs (prepared by `scripts/prepare-native-deps.sh`, not committed)
- Copyright: Copyright (c) 2022 hev

## v2rayNG

- Project: https://github.com/2dust/v2rayNG
- Pinned release used as the verified artifact source: `2.2.6`
- License: GPL-3.0

The preparation script downloads ABI-specific release APKs only to extract the HEV shared libraries, then verifies expected JNI symbols.

## Android and Kotlin libraries

AndroidX, Jetpack Compose, Kotlin, Kotlin Coroutines, and JUnit dependencies are resolved through Google Maven and Maven Central. They retain their respective licenses.
