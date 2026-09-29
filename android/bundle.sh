#!/bin/sh
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
BUNDLETOOL="$SDK/bundletool/bundletool.jar"
KS="$HOME/.baresudoku/release.jks"
ALIAS=baresudoku
PW=$(cat "$HOME/.baresudoku/password")
OUT=build/bundle
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/base/manifest" "$OUT/base/dex"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link --proto-format -o "$OUT/linked.zip" -I "$JAR" --manifest AndroidManifest.xml --proguard "$OUT/aapt.pro" "$OUT/res.zip"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" -d "$OUT/classes" $(find src -name '*.java')
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 --lib "$JAR" --pg-conf proguard.pro --pg-conf "$OUT/aapt.pro" --output "$OUT/base/dex" $(find "$OUT/classes" -name '*.class')
unzip -q "$OUT/linked.zip" -d "$OUT/linked"
mv "$OUT/linked/AndroidManifest.xml" "$OUT/base/manifest/"
mv "$OUT/linked/resources.pb" "$OUT/linked/res" "$OUT/base/"
(cd "$OUT/base" && zip -q -r -D -X ../base.zip .)
java -jar "$BUNDLETOOL" build-bundle --modules="$OUT/base.zip" --config=BundleConfig.json --output="$OUT/baresudoku.aab"
jarsigner -keystore "$KS" -storepass "$PW" -keypass "$PW" -sigalg SHA256withRSA -digestalg SHA-256 "$OUT/baresudoku.aab" "$ALIAS" > /dev/null
java -jar "$BUNDLETOOL" build-apks --bundle="$OUT/baresudoku.aab" --output="$OUT/universal.apks" --mode=universal \
  --ks="$KS" --ks-pass="pass:$PW" --ks-key-alias="$ALIAS" --key-pass="pass:$PW"
unzip -q -o "$OUT/universal.apks" universal.apk -d "$OUT"
"$BT/apksigner" verify "$OUT/universal.apk"
printf 'AAB: %s bayt, evrensel APK: %s bayt\n' "$(wc -c < "$OUT/baresudoku.aab" | tr -d ' ')" "$(wc -c < "$OUT/universal.apk" | tr -d ' ')"
