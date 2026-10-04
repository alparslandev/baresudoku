#!/bin/sh
set -e
cd "$(dirname "$0")"
mkdir -p build
clang -std=c11 -Wall -Wextra -O2 -o build/test Sudoku.c Game.c Variant.c test/test.c
build/test "$@"
