#!/bin/sh
set -e
cd "$(dirname "$0")"
MODE="${1:-sim}"
OUT="build/$MODE"
APP="$OUT/BareSudoku.app"
rm -rf "$OUT"
mkdir -p "$APP" "$OUT/icon/A.xcassets/AppIcon.appiconset"
if [ "$MODE" = sim ]; then
  SDK=iphonesimulator
  MINFLAG="-mios-simulator-version-min=15.0"
  PLATFORM=iphonesimulator
else
  SDK=iphoneos
  MINFLAG="-mios-version-min=15.0"
  PLATFORM=iphoneos
fi
xcrun -sdk $SDK clang -arch arm64 $MINFLAG -Oz -fobjc-arc -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-exceptions -fno-objc-exceptions \
  -Wall -Wno-unused-parameter $EXTRA -framework UIKit -framework Foundation -framework CoreGraphics -Wl,-dead_strip -Wl,-no_data_const -Wl,-no_function_starts \
  -o "$APP/BareSudoku" main.m Sudoku.c Game.c
strip "$APP/BareSudoku"
rsvg-convert -w 1024 -h 1024 icon.svg -o "$OUT/icon/A.xcassets/AppIcon.appiconset/icon.png"
printf '{"info":{"version":1,"author":"xcode"}}' > "$OUT/icon/A.xcassets/Contents.json"
printf '{"images":[{"filename":"icon.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"version":1,"author":"xcode"}}' > "$OUT/icon/A.xcassets/AppIcon.appiconset/Contents.json"
xcrun actool --app-icon AppIcon --output-partial-info-plist "$OUT/icon/partial.plist" --platform $PLATFORM --minimum-deployment-target 15.0 \
  --target-device iphone --optimization space --compress-pngs --compile "$APP" "$OUT/icon/A.xcassets" > "$OUT/actool.log" 2>&1
plutil -convert binary1 -o "$APP/Info.plist" Info.plist
printf 'APPL????' > "$APP/PkgInfo"
if [ "$MODE" = sim ]; then
  codesign -s - -f "$APP" > /dev/null 2>&1
fi
printf 'binary: %s bayt, Assets.car: %s bayt, bundle: %s bayt\n' "$(wc -c < "$APP/BareSudoku" | tr -d ' ')" "$(wc -c < "$APP/Assets.car" | tr -d ' ')" "$(du -sk "$APP" | cut -f1)K"
