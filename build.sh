#!/bin/sh
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
if [ -f "$HOME/.baresudoku/release.jks" ]; then
  KS="$HOME/.baresudoku/release.jks"
  ALIAS=baresudoku
  PW=$(cat "$HOME/.baresudoku/password")
else
  KS="$HOME/.android/debug.keystore"
  ALIAS=androiddebugkey
  PW=android
fi
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/linked.apk" -I "$JAR" --manifest AndroidManifest.xml --proguard "$OUT/aapt.pro" "$OUT/res.zip"
"$BT/aapt2" optimize -o "$OUT/optimized.apk" --shorten-resource-paths --collapse-resource-names "$OUT/linked.apk"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" -d "$OUT/classes" $(find src -name '*.java')
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 --lib "$JAR" --pg-conf proguard.pro --pg-conf "$OUT/aapt.pro" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/optimized.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -9 ../unaligned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PW" --ks-key-alias "$ALIAS" --key-pass "pass:$PW" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled false \
  --out "$OUT/baresudoku.apk" "$OUT/aligned.apk"
printf 'APK: %s bayt\n' "$(wc -c < "$OUT/baresudoku.apk" | tr -d ' ')"
