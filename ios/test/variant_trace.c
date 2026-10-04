#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../Variant.h"

static Variant v;

int main(int argc, char **argv) {
    int n = argc > 1 ? atoi(argv[1]) : 10;
    for (int kind = 0; kind < 3; kind++) {
        for (int level = 0; level < VARIANT_LEVELS; level++) {
            for (int seed = 1; seed <= n; seed++) {
                variant_init(&v, kind, (uint32_t)(seed * 7919 + level));
                int puzzle[81] = {0};
                variant_generate(&v, level, puzzle);
                int size = v.shape.size;
                printf("%d %d %d ", kind, level, seed);
                for (int c = 0; c < size; c++) printf("%d", puzzle[c]);
                printf(" ");
                for (int c = 0; c < size; c++) printf(c > 0 ? ",%d" : "%d", v.shape.cageOf[c]);
                printf(" ");
                for (int k = 0; k < v.shape.cageCount; k++) printf(k > 0 ? ",%d" : "%d", v.shape.cageSum[k]);
                printf(" ");
                int values[81];
                memcpy(values, puzzle, sizeof(values));
                for (int step = 0; step < size && variant_hint(&v, values, puzzle); step++) {
                    printf(step > 0 ? ",%d:%d:%d" : "%d:%d:%d", v.stepCell, v.stepDigit, v.stepUnit);
                    values[v.stepCell] = v.stepDigit;
                }
                printf("\n");
            }
        }
    }
    return 0;
}
