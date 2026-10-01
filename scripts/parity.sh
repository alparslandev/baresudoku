#!/bin/sh
set -e
cd "$(dirname "$0")/.."
WEB="${WEB:-../baresudoku-web}"
N="${1:-1000}"
OUT=build/parity
rm -rf "$OUT"
mkdir -p "$OUT/java"
bun scripts/parity.js "$WEB/src/engine.js" "$N" "$OUT/puzzles.txt" > "$OUT/js.txt"
javac -encoding UTF-8 -d "$OUT/java" android/src/com/baresudoku/Sudoku.java android/test/Trace.java
java -cp "$OUT/java" com.baresudoku.Trace "$OUT/puzzles.txt" > "$OUT/java.txt"
clang -std=c11 -Wall -Wextra -O2 -o "$OUT/trace" ios/Sudoku.c ios/test/trace.c
"$OUT/trace" "$OUT/puzzles.txt" > "$OUT/c.txt"
if cmp -s "$OUT/js.txt" "$OUT/java.txt" && cmp -s "$OUT/js.txt" "$OUT/c.txt"; then
  echo "PARITY: $N bulmaca, JS Java C ayni"
else
  echo "PARITY HATA: $OUT/js.txt java.txt c.txt farkli"
  diff "$OUT/js.txt" "$OUT/java.txt" | head -5
  diff "$OUT/js.txt" "$OUT/c.txt" | head -5
  exit 1
fi
