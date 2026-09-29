#!/bin/sh
set -e
cd "$(dirname "$0")/.."
MODE="${1:-upload}"
if [ "$MODE" = upload ] && [ -n "$(git status --porcelain)" ]; then
  echo "Calisma alani temiz degil, once commit at."
  exit 1
fi
ios/test.sh 30
PLIST=ios/Info.plist
BUILD=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$PLIST")
NEW=$((BUILD + 1))
VERSION="1.$((NEW - 1))"
if [ "$MODE" = upload ]; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $NEW" -c "Set :CFBundleShortVersionString $VERSION" "$PLIST"
fi
cd ios
rm -rf build/BareSudoku.xcarchive build/out
xcodebuild -project BareSudoku.xcodeproj -scheme BareSudoku -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/BareSudoku.xcarchive -derivedDataPath build/xc CODE_SIGNING_ALLOWED=NO -quiet archive
if [ "$MODE" = upload ]; then
  OPTIONS=ExportOptions.plist
else
  sed 's|<string>upload</string>|<string>export</string>|' ExportOptions.plist > build/export.plist
  OPTIONS=build/export.plist
fi
xcodebuild -exportArchive -archivePath build/BareSudoku.xcarchive -exportOptionsPlist "$OPTIONS" -exportPath build/out -allowProvisioningUpdates -quiet
cd ..
if [ "$MODE" != upload ]; then
  printf 'IPA: %s bayt (%s)\n' "$(wc -c < "ios/build/out/Bare Sudoku.ipa" | tr -d ' ')" "ios/build/out/Bare Sudoku.ipa"
  exit 0
fi
git add ios/Info.plist
git commit -q -m "iOS surum $VERSION"
git tag "ios-v$VERSION"
git push -q
git push -q --tags
echo "iOS $VERSION (build $NEW) App Store Connect'e yuklendi"
