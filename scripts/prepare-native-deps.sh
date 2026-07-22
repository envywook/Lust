#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/.native-build"
XRAY_TAG="v26.7.19"
XRAY_SHA256="0242df3843d59bb9aa68ddd52cb2ca443871a24b1f2bee4d5a346e74d5e4ee5d"
V2RAYNG_TAG="2.2.6"
SING_BOX_VERSION="1.13.14"
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
declare -A APK_NAME APK_URL APK_SHA256
SING_ARCH=(arm arm64 386 amd64)
declare -A SING_ABI SING_SHA256
SING_ABI[arm]="armeabi-v7a"
SING_ABI[arm64]="arm64-v8a"
SING_ABI[386]="x86"
SING_ABI[amd64]="x86_64"
SING_SHA256[arm]="f650977ff36f50ed59135ee8344c622ba737fbb9ff343b9e200fea246a81c181"
SING_SHA256[arm64]="59a4d18a4108e2f2a1bd49ca547829112712123975092d4a4bf1f443b6f3d747"
SING_SHA256[386]="c8c146d199a1f6ccd2e00baae10bc3609aa23326ea81785ad652eebcfec516c5"
SING_SHA256[amd64]="b3a265a7e2dca1a5e77aaa666ad38a155be0d00c14f8b06bba35753f64b13da1"
APK_NAME[armeabi-v7a]='v2rayNG_2.2.6_armeabi-v7a.apk'
APK_URL[armeabi-v7a]='https://github.com/2dust/v2rayNG/releases/download/2.2.6/v2rayNG_2.2.6_armeabi-v7a.apk'
APK_SHA256[armeabi-v7a]='c5149bacb770a4c0e78c2caf6a6b5fda4a4c156f5940cf1d9d7e6214395f7ab2'
APK_NAME[arm64-v8a]='v2rayNG_2.2.6_arm64-v8a.apk'
APK_URL[arm64-v8a]='https://github.com/2dust/v2rayNG/releases/download/2.2.6/v2rayNG_2.2.6_arm64-v8a.apk'
APK_SHA256[arm64-v8a]='62b0675c0986a8613f1bb67f1a68c366c6357e04a68687a3bd907170594c437d'
APK_NAME[x86]='v2rayNG_2.2.6_x86.apk'
APK_URL[x86]='https://github.com/2dust/v2rayNG/releases/download/2.2.6/v2rayNG_2.2.6_x86.apk'
APK_SHA256[x86]='6b597c88f5e2181dbc2a20f205dfd8580b5546a048a5dc113f3e151303a6573f'
APK_NAME[x86_64]='v2rayNG_2.2.6_x86_64.apk'
APK_URL[x86_64]='https://github.com/2dust/v2rayNG/releases/download/2.2.6/v2rayNG_2.2.6_x86_64.apk'
APK_SHA256[x86_64]='d8420ab763cb099f7d1ee45a8cad023e9cac59c7429a12281439a905975509ff'
command -v curl >/dev/null
command -v unzip >/dev/null
command -v readelf >/dev/null
command -v strings >/dev/null
command -v tar >/dev/null
mkdir -p "$ROOT/app/libs" "$ROOT/app/src/main/jniLibs" "$WORK/v2rayng-apks"
curl -fsSL --retry 3 -o "$ROOT/app/libs/libv2ray.aar" \
  "https://github.com/2dust/AndroidLibXrayLite/releases/download/$XRAY_TAG/libv2ray.aar"
echo "$XRAY_SHA256  $ROOT/app/libs/libv2ray.aar" | sha256sum -c -
for abi in "${ABIS[@]}"; do
  apk="$WORK/v2rayng-apks/${APK_NAME[$abi]}"
  curl -fsSL --retry 3 -o "$apk" "${APK_URL[$abi]}"
  echo "${APK_SHA256[$abi]}  $apk" | sha256sum -c -
  dest="$ROOT/app/src/main/jniLibs/$abi/libhev-socks5-tunnel.so"
  mkdir -p "$(dirname "$dest")"
  unzip -p "$apk" "lib/$abi/libhev-socks5-tunnel.so" > "$dest"
  test -s "$dest"
  symbols="$WORK/v2rayng-apks/${abi}-symbols.txt"
  readelf --dyn-syms --wide "$dest" > "$symbols"
  grep -q 'JNI_OnLoad' "$symbols"
  strings_file="$WORK/v2rayng-apks/${abi}-strings.txt"
  strings "$dest" > "$strings_file"
  grep -q 'com/v2ray/ang/service/TProxyService' "$strings_file"
done
for arch in "${SING_ARCH[@]}"; do
  archive="$WORK/sing-box-$SING_BOX_VERSION-android-$arch.tar.gz"
  curl -fsSL --retry 3 -o "$archive" \
    "https://github.com/SagerNet/sing-box/releases/download/v$SING_BOX_VERSION/sing-box-$SING_BOX_VERSION-android-$arch.tar.gz"
  echo "${SING_SHA256[$arch]}  $archive" | sha256sum -c -
  abi="${SING_ABI[$arch]}"
  dest="$ROOT/app/src/main/jniLibs/$abi/libsingbox.so"
  tar -xOzf "$archive" "sing-box-$SING_BOX_VERSION-android-$arch/sing-box" > "$dest"
  chmod 755 "$dest"
  readelf -h "$dest" | grep -q 'DYN (Position-Independent Executable file)'
done
printf 'Xray AAR:\n'; sha256sum "$ROOT/app/libs/libv2ray.aar"
printf 'HEV JNI libraries from v2rayNG %s:\n' "$V2RAYNG_TAG"
find "$ROOT/app/src/main/jniLibs" -type f -name 'libhev-socks5-tunnel.so' -print0 | sort -z | xargs -0 sha256sum
printf 'sing-box Android executables %s:\n' "$SING_BOX_VERSION"
find "$ROOT/app/src/main/jniLibs" -type f -name 'libsingbox.so' -print0 | sort -z | xargs -0 sha256sum
