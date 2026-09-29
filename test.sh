#!/bin/sh
set -e
cd "$(dirname "$0")"
rm -rf build/test
mkdir -p build/test
javac -encoding UTF-8 -d build/test $(find src -name '*.java' ! -name 'MainActivity.java' ! -name 'BoardView.java') test/SudokuTest.java
java -cp build/test com.baresudoku.SudokuTest "$@"
