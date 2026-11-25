#!/usr/bin/env bash
# ChargeMeter 构建脚本（无需 Gradle）
#
# 依赖：
#   - JDK 17+
#   - Android build-tools（aapt2 / zipalign / d8 / apksigner）
#   - Android platform 的 android.jar
#   - 在 ARM 主机上运行 x86_64 的 aapt2/zipalign 时，需要 qemu-user-static
#
# 环境变量（都有默认值）：
#   ANDROID_SDK_ROOT  SDK 根目录，默认 $HOME/android/sdk
#   BUILD_TOOLS_DIR   build-tools 目录，默认 $ANDROID_SDK_ROOT/android-14
#   ANDROID_JAR       平台 android.jar，默认 $ANDROID_SDK_ROOT/android-36/android.jar
#   QEMU              aapt2/zipalign 运行前缀（ARM 上设为 qemu-x86_64-static，x86 上留空）
set -e

SDK="${ANDROID_SDK_ROOT:-$HOME/android/sdk}"
BT="${BUILD_TOOLS_DIR:-$SDK/android-14}"
PLAT="${ANDROID_JAR:-$SDK/android-36/android.jar}"
Q="${QEMU:-}"

P="$(cd "$(dirname "$0")" && pwd)"
OUT="$P/build"
KS="$P/signing.keystore"
VERSION_CODE="24"
VERSION_NAME="6.7"
ALIAS="${KEY_ALIAS:-dsh}"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

echo "== compile resources"
$Q "$BT/aapt2" compile --dir "$P/res" -o "$OUT/res.zip"

echo "== link"
$Q "$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLAT" \
  --manifest "$P/AndroidManifest.xml" \
  "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 29 --target-sdk-version 36 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

echo "== javac"
find "$P/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 17 -target 17 -classpath "$PLAT" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "== d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 29 \
  --lib "$PLAT" --output "$OUT" @"$OUT/classes.txt"

echo "== package"
python3 - "$OUT" <<'PY'
import sys, zipfile, os
out = sys.argv[1]
src = os.path.join(out, "base.apk")
dst = os.path.join(out, "unsigned.apk")
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zout.write(os.path.join(out, "classes.dex"), "classes.dex")
print("packed", os.path.getsize(dst))
PY

echo "== zipalign"
$Q "$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
if [ ! -f "$KS" ]; then
  echo "生成调试签名（正式发布请替换为你自己的 keystore）"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=ChargeMeter, OU=Dev, O=ChargeMeter, C=CN"
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias "$ALIAS" \
  --out "$P/chargemeter.apk" "$OUT/aligned.apk"

echo "== done -> $P/chargemeter.apk"
