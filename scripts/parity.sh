#!/bin/sh
set -e
cd "$(dirname "$0")/.."
WEB="${WEB:-../baresudoku-web}"
N="${1:-1000}"
OUT=build/parity
rm -rf "$OUT"
mkdir -p "$OUT/java"
javac -encoding UTF-8 -d "$OUT/java" android/src/com/baresudoku/Sudoku.java android/test/Trace.java
clang -std=c11 -Wall -Wextra -O2 -o "$OUT/trace" ios/Sudoku.c ios/test/trace.c
for OFF in ""; do
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
