#!/bin/sh
set -e
cd "$(dirname "$0")/../ios"
rm -rf build/BareSudoku.xcarchive build/out
xcodebuild -project BareSudoku.xcodeproj -scheme BareSudoku -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/BareSudoku.xcarchive -derivedDataPath build/xc CODE_SIGNING_ALLOWED=NO -quiet archive
sed 's|<string>upload</string>|<string>export</string>|' ExportOptions.plist > build/export.plist
xcodebuild -exportArchive -archivePath build/BareSudoku.xcarchive -exportOptionsPlist build/export.plist -exportPath build/out -allowProvisioningUpdates -quiet
printf 'IPA: %s bayt (%s)\n' "$(wc -c < "build/out/Bare Sudoku.ipa" | tr -d ' ')" "ios/build/out/Bare Sudoku.ipa"
