#!/bin/sh
set -e
cd "$(dirname "$0")/.."
if [ -n "$(git status --porcelain)" ]; then
  echo "Calisma alani temiz degil, once commit at."
  exit 1
fi
if [ ! -f "$HOME/.baresudoku/release.jks" ]; then
  echo "Yayin anahtari yok: ~/.baresudoku/release.jks"
  exit 1
fi
./test.sh 30
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' AndroidManifest.xml)
NEW=$((CODE + 1))
VERSION="1.$((NEW - 1))"
sed -i '' "s/android:versionCode=\"$CODE\"/android:versionCode=\"$NEW\"/; s/android:versionName=\"[^\"]*\"/android:versionName=\"$VERSION\"/" AndroidManifest.xml
./build.sh
SIZE=$(wc -c < build/baresudoku.apk | tr -d ' ')
sh scripts/site.sh
git add AndroidManifest.xml
git commit -q -m "Surum $VERSION"
git tag "v$VERSION"
git push -q
git push -q --tags
gh release create "v$VERSION" build/baresudoku.apk --title "Bare Sudoku $VERSION" --notes "APK $SIZE bytes, Android 8+."
wrangler deploy
echo "Surum $VERSION yayinda, APK $SIZE bayt"
