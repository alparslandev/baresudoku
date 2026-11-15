#ifndef VARIANT_H
#define VARIANT_H

#include <stdint.h>

#define VARIANT_KIND_DIAGONAL 0
#define VARIANT_KIND_KILLER 1
#define VARIANT_KIND_MINI 2
#define VARIANT_LEVELS 3
#define VARIANT_UNIT_ROW 0
#define VARIANT_UNIT_COL 1
#define VARIANT_UNIT_BOX 2
#define VARIANT_UNIT_NAKED 3
#define VARIANT_UNIT_DIAGONAL 4
#define VARIANT_UNIT_REVEAL 5
#define VARIANT_MAX_CELLS 81
#define VARIANT_MAX_UNITS 29
#define VARIANT_MAX_CAGES 81
#define VARIANT_MAX_PEERS 32
#define VARIANT_COMBO_SLOTS 16
#define VARIANT_NODE_LIMIT 400000

typedef struct VariantShape {
    int kind;
    int n;
    int boxH;
    int boxW;
    int size;
    int all;
    int unitCount;
    int unitCells[VARIANT_MAX_UNITS * 9];
    int unitType[VARIANT_MAX_UNITS];
    int cageOf[VARIANT_MAX_CELLS];
    int cageCount;
    int cageSize[VARIANT_MAX_CAGES];
    int cageSum[VARIANT_MAX_CAGES];
    int cageCells[VARIANT_MAX_CAGES * 9];
    int peerCount[VARIANT_MAX_CELLS];
    int peerCells[VARIANT_MAX_CELLS * VARIANT_MAX_PEERS];
} VariantShape;

typedef struct Variant {
    VariantShape shape;
    uint32_t state;
    int values[VARIANT_MAX_CELLS];
    int found[VARIANT_MAX_CELLS];
    int other[VARIANT_MAX_CELLS];
    int solution[VARIANT_MAX_CELLS];
    int full[VARIANT_MAX_CELLS];
    int puzzle[VARIANT_MAX_CELLS];
    int order[VARIANT_MAX_CELLS];
    int cages[VARIANT_MAX_CELLS];
    int sums[VARIANT_MAX_CAGES];
    int members[4];
    int keyGivens[VARIANT_MAX_CELLS];
    int keyCages[VARIANT_MAX_CELLS];
    int keySums[VARIANT_MAX_CAGES];
    int keyCount;
    int keyValid;
    int count;
    int limit;
    int nodes;
    int stepCell;
    int stepDigit;
    int stepUnit;
    int hintTech;
    int combos[10 * 46 * VARIANT_COMBO_SLOTS];
    int comboCount[10 * 46];
} Variant;

void variant_shape_init(VariantShape *s, int kind);
void variant_set_cages(VariantShape *s, const int *cells, const int *sums, int count);
int variant_seen(const VariantShape *s, const int *values, int cell);
int variant_cage_head(const VariantShape *s, int cell);
void variant_init(Variant *v, int kind, uint32_t seed);
void variant_seed(Variant *v, int seed);
int variant_candidates_at(const Variant *v, const int *values, int cell);
int variant_count_solutions(Variant *v, const int *puzzle, int limit);
void variant_generate(Variant *v, int level, int *out);
int variant_hint(Variant *v, const int *values, const int *givens);

#endif
