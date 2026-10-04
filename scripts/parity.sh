#!/bin/sh
set -e
cd "$(dirname "$0")/.."
WEB="${WEB:-../baresudoku-web}"
N="${1:-1000}"
OUT=build/parity
rm -rf "$OUT"
mkdir -p "$OUT/java"
javac -encoding UTF-8 -d "$OUT/java" android/src/com/baresudoku/Sudoku.java android/src/com/baresudoku/Variant.java android/test/Trace.java android/test/VariantTrace.java
clang -std=c11 -Wall -Wextra -O2 -o "$OUT/trace" ios/Sudoku.c ios/test/trace.c
clang -std=c11 -Wall -Wextra -O2 -o "$OUT/variant" ios/Variant.c ios/test/variant_trace.c
VN=$(( N / 50 + 4 ))
bun scripts/variant-parity.js "$WEB/src/variant.js" "$VN" > "$OUT/vjs.txt"
java -cp "$OUT/java" com.baresudoku.VariantTrace "$VN" > "$OUT/vjava.txt"
"$OUT/variant" "$VN" > "$OUT/vc.txt"
if cmp -s "$OUT/vjs.txt" "$OUT/vjava.txt" && cmp -s "$OUT/vjs.txt" "$OUT/vc.txt"; then
  echo "PARITY: $(wc -l < "$OUT/vjs.txt" | tr -d ' ') varyant bulmacasi (kafesler ve ipucu yolu dahil), JS Java C ayni"
else
  echo "PARITY HATA: varyant, $OUT/vjs.txt vjava.txt vc.txt farkli"
  diff "$OUT/vjs.txt" "$OUT/vjava.txt" | head -5
  diff "$OUT/vjs.txt" "$OUT/vc.txt" | head -5
  exit 1
fi
CHAINS=27,28,29,30,31,32,33,34,35,36
for OFF in "" 37 37,38 "$CHAINS,37,38,39" "$CHAINS,37,38,39,40" "14,15,16,17,18,19,20,21,22,24,25,26,42,43,44,$CHAINS"; do
  bun scripts/parity.js "$WEB/src/engine.js" "$N" "$OUT/puzzles.txt" "$OFF" > "$OUT/js.txt"
  java -cp "$OUT/java" com.baresudoku.Trace "$OUT/puzzles.txt" "$OFF" > "$OUT/java.txt"
  "$OUT/trace" "$OUT/puzzles.txt" "$OFF" > "$OUT/c.txt"
  if cmp -s "$OUT/js.txt" "$OUT/java.txt" && cmp -s "$OUT/js.txt" "$OUT/c.txt"; then
    echo "PARITY: $(wc -l < "$OUT/puzzles.txt" | tr -d ' ') bulmaca, kapali teknikler [${OFF:-yok}], JS Java C ayni"
  else
    echo "PARITY HATA: kapali teknikler [${OFF:-yok}], $OUT/js.txt java.txt c.txt farkli"
    diff "$OUT/js.txt" "$OUT/java.txt" | head -5
    diff "$OUT/js.txt" "$OUT/c.txt" | head -5
    exit 1
  fi
done
