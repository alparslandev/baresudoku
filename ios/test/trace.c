#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../Sudoku.h"

static unsigned h1;
static unsigned h2;

static void mix(int x) {
    h1 = (h1 ^ (unsigned)x) * 16777619u;
    h2 = h2 * 31u + (unsigned)x;
}

static Sudoku engine;

static void mixFigures(const Sudoku *e) {
    mix(e->figCount);
    int cell = 0;
    int elim = 0;
    for (int f = 0; f < e->figCount; f++) {
        mix(e->figKind[f]);
        mix(e->figDigits[f]);
        mix(e->figA[f]);
        mix(e->figB[f]);
        for (; cell < e->figCellEnd[f]; cell++) mix(e->figCells[cell]);
        for (; elim < e->figElimEnd[f]; elim++) {
            mix(e->figElimCell[elim]);
            mix(e->figElimMask[elim]);
        }
    }
    mix(e->conflictState);
    mix(e->conflictCell);
    mix(e->conflictUnit);
    mix(e->conflictDigits);
}

int main(int argc, char **argv) {
    if (argc < 2) return 2;
    FILE *f = fopen(argv[1], "r");
    if (!f) return 2;
    sudoku_init(&engine, 1);
    for (char *id = argc > 2 ? argv[2] : ""; *id; ) {
        engine.techOff[atoi(id)] = 1;
        while (*id && *id != ',') id++;
        if (*id == ',') id++;
    }
    engine.tracing = 1;
    Sudoku *e = &engine;
    char line[256];
    int index = 0;
    while (fgets(line, sizeof line, f)) {
        if (strlen(line) < 81) continue;
        int puzzle[81];
        for (int c = 0; c < 81; c++) puzzle[c] = line[c] - '0';
        h1 = 0x811c9dc5u;
        h2 = 0;
        sudoku_load(e, puzzle);
        int steps = 0;
        while (!sudoku_complete(e) && !sudoku_stuck(e)) {
            int t = sudoku_step(e);
            mix(t);
            if (t < 0) break;
            steps++;
            mix(e->stepRating);
            if (t == 0) {
                mix(e->stepCell);
                mix(e->stepDigit);
            }
            for (int c = 0; c < 81; c++) mix(e->lc[c]);
            mixFigures(e);
        }
        int rating = sudoku_rate(e, puzzle);
        printf("%d %d %d %d %d ", index, steps, rating, e->rateOrder, e->rateTech);
        if (sudoku_hint(e, puzzle, puzzle)) printf("%d/%d/%d", e->hintTech, e->stepCell, e->stepDigit);
        else printf("x");
        printf(" %x %x\n", h1, h2);
        index++;
    }
    fclose(f);
    return 0;
}
