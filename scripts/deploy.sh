#!/bin/sh
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
WEB="${BARESUDOKU_WEB:-$ROOT/../baresudoku-web}"
ASC="$HOME/.baresudoku/asc.json"
PLAY="$HOME/.baresudoku/play.json"
PLATFORMS="${PLATFORMS:-ios mac tv vision}"
STEP=""
step() { STEP="$1"; printf '\n== %s\n' "$1"; }
trap 'test $? -eq 0 || printf "\nBasarisiz adim: %s\n" "$STEP"' EXIT

step "On kontrol"
[ -z "$(git status --porcelain)" ] || { echo "Calisma alani temiz degil, once commit at."; exit 1; }
[ -d "$WEB" ] || { echo "Web deposu yok: $WEB"; exit 1; }
[ -z "$(git -C "$WEB" status --porcelain)" ] || { echo "Web deposu temiz degil: $WEB"; exit 1; }
[ -f "$HOME/.baresudoku/release.jks" ] || { echo "Yayin anahtari yok: ~/.baresudoku/release.jks"; exit 1; }
AUTH=""
if [ -f "$ASC" ]; then
  ASC_ON=1
  AUTH="-authenticationKeyPath $(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["key"])' "$ASC") -authenticationKeyID $(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["keyId"])' "$ASC") -authenticationKeyIssuerID $(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["issuerId"])' "$ASC")"
  echo "App Store: API anahtari var, yukleme ve inceleme yapilacak"
else
  ASC_ON=0
  echo "App Store: API anahtari yok ($ASC), yukleme ve inceleme atlanacak"
fi
if [ -f "$PLAY" ]; then PLAY_ON=1; echo "Google Play: servis hesabi var, yukleme yapilacak"; else PLAY_ON=0; echo "Google Play: servis hesabi yok ($PLAY), yukleme atlanacak"; fi

step "Testler"
android/test.sh 30
ios/test.sh 30
(cd "$WEB" && bun test)

step "Surum"
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' android/AndroidManifest.xml)
NEW=$((CODE + 1))
VERSION="1.$((NEW - 1))"
sed -i '' "s/android:versionCode=\"$CODE\"/android:versionCode=\"$NEW\"/; s/android:versionName=\"[^\"]*\"/android:versionName=\"$VERSION\"/" android/AndroidManifest.xml
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $NEW" -c "Set :CFBundleShortVersionString $VERSION" ios/Info.plist
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $NEW" -c "Set :CFBundleShortVersionString $VERSION" ios/Info-tv.plist
echo "Surum $VERSION, derleme $NEW"

step "Android APK ve AAB"
android/build.sh
android/bundle.sh
APK_SIZE=$(wc -c < android/build/baresudoku.apk | tr -d ' ')

archive() {
  rm -rf "ios/build/$1.xcarchive" "ios/build/out-$1"
  (cd ios && xcodebuild -project BareSudoku.xcodeproj -scheme "$2" -configuration Release -destination "$3" \
    -archivePath "build/$1.xcarchive" -derivedDataPath build/xc CODE_SIGNING_ALLOWED=NO -quiet archive)
}
export_archive() {
  if [ "$2" = upload ]; then
    OPTIONS=ExportOptions.plist
  else
    sed 's|<string>upload</string>|<string>export</string>|' ios/ExportOptions.plist > ios/build/export.plist
    OPTIONS=build/export.plist
  fi
  (cd ios && xcodebuild -exportArchive -archivePath "build/$1.xcarchive" -exportOptionsPlist "$OPTIONS" -exportPath "build/out-$1" -allowProvisioningUpdates $AUTH -quiet)
}
for P in $PLATFORMS; do
  case $P in
    ios) step "iOS arsiv"; archive ios BareSudoku 'generic/platform=iOS' ;;
    mac) step "macOS arsiv"; archive mac BareSudoku 'generic/platform=macOS,variant=Mac Catalyst'
         codesign --force --sign - --entitlements ios/Mac.entitlements ios/build/mac.xcarchive/Products/Applications/*.app ;;
    vision) step "visionOS arsiv"; archive vision BareSudoku 'generic/platform=visionOS' ;;
    tv) step "tvOS arsiv"; archive tv BareSudokuTV 'generic/platform=tvOS' ;;
    *) echo "Bilinmeyen platform: $P"; exit 1 ;;
  esac
  if [ "$ASC_ON" = 1 ]; then
    step "$P App Store Connect yukleme"
    export_archive "$P" upload
  else
    step "$P paket (yuklenmeden)"
    export_archive "$P" export
    ls -l "ios/build/out-$P" | awk 'NR>1 && $5 > 0 {print $9 ": " $5 " bayt"}'
  fi
done

step "Commit, etiket, GitHub surumu"
git add android/AndroidManifest.xml ios/Info.plist ios/Info-tv.plist
git commit -q -m "Surum $VERSION"
git tag "v$VERSION"
git push -q
git push -q --tags
gh release create "v$VERSION" android/build/baresudoku.apk android/build/bundle/baresudoku.aab --title "Bare Sudoku $VERSION" \
  --notes "APK $APK_SIZE bytes, Android 8+. The AAB is the Google Play bundle."

if [ "$PLAY_ON" = 1 ]; then
  step "Google Play"
  bun scripts/play.js release android/build/bundle/baresudoku.aab "$VERSION" "${PLAY_TRACK:-production}"
fi
if [ "$ASC_ON" = 1 ]; then
  for P in $PLATFORMS; do
    step "$P App Store incelemeye gonderme"
    bun scripts/asc.js submit "$VERSION" "$NEW" "$P"
  done
fi

step "Web"
(cd "$WEB" && bun run deploy)

printf '\nSurum %s yayinda: APK %s bayt, platformlar: %s, Play: %s, App Store: %s\n' "$VERSION" "$APK_SIZE" "$PLATFORMS" \
  "$([ "$PLAY_ON" = 1 ] && echo yuklendi || echo atlandi)" "$([ "$ASC_ON" = 1 ] && echo gonderildi || echo atlandi)"
