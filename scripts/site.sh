#!/bin/sh
set -e
cd "$(dirname "$0")/.."
if [ ! -f build/baresudoku.apk ]; then
  echo "Once ./build.sh veya bun run deploy calistir, build/baresudoku.apk yok."
  exit 1
fi
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
SIZE=$(wc -c < build/baresudoku.apk | tr -d ' ')
KB=$(( (SIZE + 512) / 1024 ))
rm -rf build/site
mkdir -p build/site
cp build/baresudoku.apk build/site/baresudoku.apk
cp site/_headers build/site/_headers
sed "s/{{VERSION}}/$VERSION/g; s/{{SIZE}}/$KB/g" site/index.html > build/site/index.html
echo "Site hazir: surum $VERSION, APK $SIZE bayt"
