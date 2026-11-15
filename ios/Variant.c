#include "Variant.h"
#include <string.h>

static int addUnit(VariantShape *s, int type) {
    int u = s->unitCount++;
    s->unitType[u] = type;
    return u;
}

static int sharesGroup(const VariantShape *s, int a, int b) {
    for (int u = 0; u < s->unitCount; u++) {
        int ha = 0, hb = 0;
        for (int k = 0; k < s->n; k++) {
            int c = s->unitCells[u * 9 + k];
            if (c == a) ha = 1;
            if (c == b) hb = 1;
        }
        if (ha && hb) return 1;
    }
    return s->cageOf[a] >= 0 && s->cageOf[a] == s->cageOf[b];
}

static void buildPeers(VariantShape *s) {
    for (int a = 0; a < s->size; a++) {
        int m = 0;
        for (int b = 0; b < s->size; b++) if (b != a && sharesGroup(s, a, b)) s->peerCells[a * VARIANT_MAX_PEERS + m++] = b;
        s->peerCount[a] = m;
    }
}

void variant_shape_init(VariantShape *s, int kind) {
    memset(s, 0, sizeof(*s));
    s->kind = kind;
    s->n = kind == VARIANT_KIND_MINI ? 6 : 9;
    s->boxH = kind == VARIANT_KIND_MINI ? 2 : 3;
    s->boxW = 3;
    s->size = s->n * s->n;
    s->all = (1 << s->n) - 1;
    for (int c = 0; c < VARIANT_MAX_CELLS; c++) s->cageOf[c] = -1;
    int n = s->n;
    for (int r = 0; r < n; r++) {
        int u = addUnit(s, VARIANT_UNIT_ROW);
        for (int k = 0; k < n; k++) s->unitCells[u * 9 + k] = r * n + k;
    }
    for (int c = 0; c < n; c++) {
        int u = addUnit(s, VARIANT_UNIT_COL);
        for (int k = 0; k < n; k++) s->unitCells[u * 9 + k] = k * n + c;
    }
    int across = n / s->boxW;
    for (int b = 0; b < n; b++) {
        int top = b / across * s->boxH, left = b % across * s->boxW;
        int u = addUnit(s, VARIANT_UNIT_BOX);
        for (int k = 0; k < n; k++) s->unitCells[u * 9 + k] = (top + k / s->boxW) * n + left + k % s->boxW;
    }
    if (kind == VARIANT_KIND_DIAGONAL) {
        int down = addUnit(s, VARIANT_UNIT_DIAGONAL);
        for (int k = 0; k < n; k++) s->unitCells[down * 9 + k] = k * n + k;
        int up = addUnit(s, VARIANT_UNIT_DIAGONAL);
        for (int k = 0; k < n; k++) s->unitCells[up * 9 + k] = k * n + n - 1 - k;
    }
    buildPeers(s);
}

void variant_set_cages(VariantShape *s, const int *cells, const int *sums, int count) {
    for (int c = 0; c < VARIANT_MAX_CELLS; c++) s->cageOf[c] = -1;
    s->cageCount = 0;
    for (int k = 0; k < count; k++) {
        s->cageSize[k] = 0;
        s->cageSum[k] = sums[k];
    }
    for (int c = 0; c < s->size; c++) {
        int k = cells[c];
        if (k < 0) continue;
        s->cageOf[c] = k;
        s->cageCells[k * 9 + s->cageSize[k]++] = c;
        if (k + 1 > s->cageCount) s->cageCount = k + 1;
    }
    buildPeers(s);
}

int variant_seen(const VariantShape *s, const int *values, int cell) {
    int used = 0;
    for (int k = 0; k < s->peerCount[cell]; k++) {
        int v = values[s->peerCells[cell * VARIANT_MAX_PEERS + k]];
        if (v != 0) used |= 1 << (v - 1);
    }
    return s->all & ~used;
}

int variant_cage_head(const VariantShape *s, int cell) {
    int k = s->cageOf[cell];
    return k >= 0 && s->cageCells[k * 9] == cell;
}

void variant_init(Variant *v, int kind, uint32_t seed) {
    memset(v, 0, sizeof(*v));
    variant_shape_init(&v->shape, kind);
    v->state = seed;
    v->stepCell = -1;
    for (int m = 1; m < 512; m++) {
        int size = 0, sum = 0;
        for (int d = 1; d <= 9; d++) {
            if ((m & (1 << (d - 1))) != 0) {
                size++;
                sum += d;
            }
        }
        int at = size * 46 + sum;
        v->combos[at * VARIANT_COMBO_SLOTS + v->comboCount[at]++] = m;
    }
}

void variant_seed(Variant *v, int seed) {
    v->state = (uint32_t)seed;
}

static uint32_t nextRandom(Variant *v) {
    v->state += 0x6D2B79F5u;
    uint32_t t = v->state;
    t = (t ^ (t >> 15)) * (t | 1u);
    t ^= t + (t ^ (t >> 7)) * (t | 61u);
    return t ^ (t >> 14);
}

static int nextInt(Variant *v, int bound) {
    return (int)(nextRandom(v) % (uint32_t)bound);
}

static void shuffle(Variant *v, int *a, int length) {
    for (int i = length - 1; i > 0; i--) {
        int j = nextInt(v, i + 1);
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}

static int cageAllowed(const Variant *v, const int *values, int cell) {
    const VariantShape *s = &v->shape;
    int k = s->cageOf[cell];
    if (k < 0) return s->all;
    int used = 0, sum = 0, left = 0;
    for (int i = 0; i < s->cageSize[k]; i++) {
        int d = values[s->cageCells[k * 9 + i]];
        if (d != 0) {
            used |= 1 << (d - 1);
            sum += d;
        } else left++;
    }
    int rest = s->cageSum[k] - sum;
    if (left == 0 || rest < 1 || rest > 45) return 0;
    int at = left * 46 + rest;
    int mask = 0;
    for (int i = 0; i < v->comboCount[at]; i++) {
        int m = v->combos[at * VARIANT_COMBO_SLOTS + i];
        if ((m & used) == 0 && (m & ~s->all) == 0) mask |= m;
    }
    return mask;
}

int variant_candidates_at(const Variant *v, const int *values, int cell) {
    return variant_seen(&v->shape, values, cell) & cageAllowed(v, values, cell);
}

static void search(Variant *v) {
    const VariantShape *s = &v->shape;
    int *values = v->values;
    int best = -1, bestMask = 0, bestCount = 10;
    for (int c = 0; c < s->size; c++) {
        if (values[c] != 0) continue;
        int m = variant_candidates_at(v, values, c);
        int n = 0;
        for (int d = 0; d < s->n; d++) if ((m & (1 << d)) != 0) n++;
        if (n == 0) return;
        if (n < bestCount) {
            best = c;
            bestMask = m;
            bestCount = n;
            if (n == 1) break;
        }
    }
    v->nodes++;
    if (best < 0) {
        if (v->count == 0) for (int c = 0; c < s->size; c++) v->found[c] = values[c];
        else if (v->count == 1) for (int c = 0; c < s->size; c++) v->other[c] = values[c];
        v->count++;
        return;
    }
    for (int d = 1; d <= s->n && v->count < v->limit && v->nodes < VARIANT_NODE_LIMIT; d++) {
        if ((bestMask & (1 << (d - 1))) == 0) continue;
        values[best] = d;
        search(v);
        values[best] = 0;
    }
}

static int candidatesWithout(Variant *v, int cell) {
    int d = v->values[cell];
    v->values[cell] = 0;
    int m = variant_candidates_at(v, v->values, cell);
    v->values[cell] = d;
    return m;
}

int variant_count_solutions(Variant *v, const int *puzzle, int limit) {
    const VariantShape *s = &v->shape;
    for (int c = 0; c < s->size; c++) v->values[c] = puzzle[c];
    for (int c = 0; c < s->size; c++) if (puzzle[c] != 0 && (candidatesWithout(v, c) & (1 << (puzzle[c] - 1))) == 0) return 0;
    v->count = 0;
    v->limit = limit;
    v->nodes = 0;
    search(v);
    return v->nodes >= VARIANT_NODE_LIMIT ? limit : v->count;
}

static int fill(Variant *v, int cell) {
    const VariantShape *s = &v->shape;
    int *values = v->values;
    if (cell == s->size) return 1;
    int m = variant_seen(s, values, cell);
    int order[9];
    for (int i = 0; i < 9; i++) order[i] = i + 1;
    shuffle(v, order, s->n);
    for (int i = 0; i < s->n; i++) {
        int d = order[i];
        if ((m & (1 << (d - 1))) == 0) continue;
        values[cell] = d;
        if (fill(v, cell + 1)) return 1;
    }
    values[cell] = 0;
    return 0;
}

static void fullGrid(Variant *v) {
    VariantShape *s = &v->shape;
    for (int c = 0; c < s->size; c++) {
        v->values[c] = 0;
        v->cages[c] = -1;
    }
    variant_set_cages(s, v->cages, v->sums, 0);
    fill(v, 0);
    for (int c = 0; c < s->size; c++) v->full[c] = v->values[c];
}

static void makeCages(Variant *v) {
    VariantShape *s = &v->shape;
    int n = s->n;
    const int *full = v->full;
    int *cageOf = v->cages, *order = v->order, *members = v->members;
    for (int c = 0; c < s->size; c++) {
        cageOf[c] = -1;
        order[c] = c;
    }
    shuffle(v, order, s->size);
    int count = 0;
    for (int i = 0; i < s->size; i++) {
        int start = order[i];
        if (cageOf[start] >= 0) continue;
        int k = count++;
        int want = 2 + nextInt(v, 3);
        int size = 1;
        members[0] = start;
        int used = 1 << (full[start] - 1);
        cageOf[start] = k;
        for (int grow = 0; grow < 12 && size < want; grow++) {
            int from = members[nextInt(v, size)];
            int way = nextInt(v, 4);
            int dir = way == 0 ? -1 : way == 1 ? 1 : way == 2 ? -n : n;
            int to = from + dir;
            if (to < 0 || to >= s->size) continue;
            if ((dir == -1 || dir == 1) && to / n != from / n) continue;
            if (cageOf[to] >= 0 || (used & (1 << (full[to] - 1))) != 0) continue;
            cageOf[to] = k;
            used |= 1 << (full[to] - 1);
            members[size++] = to;
        }
        int sum = 0;
        for (int m = 0; m < size; m++) sum += full[members[m]];
        v->sums[k] = sum;
    }
    variant_set_cages(s, cageOf, v->sums, count);
}

static int singlesSolve(Variant *v, const int *puzzle) {
    const VariantShape *s = &v->shape;
    int *values = v->values;
    for (int c = 0; c < s->size; c++) values[c] = puzzle[c];
    for (;;) {
        int progress = 0, empty = 0;
        for (int c = 0; c < s->size; c++) {
            if (values[c] != 0) continue;
            empty++;
            int m = variant_candidates_at(v, values, c);
            if (m == 0) return 0;
            if ((m & (m - 1)) == 0) {
                values[c] = __builtin_ctz((unsigned)m) + 1;
                progress = 1;
            }
        }
        if (empty == 0) return 1;
        for (int u = 0; u < s->unitCount && !progress; u++) {
            for (int d = 1; d <= s->n && !progress; d++) {
                int where = -1, places = 0, placed = 0;
                for (int k = 0; k < s->n; k++) {
                    int c = s->unitCells[u * 9 + k];
                    if (values[c] == d) placed = 1;
                    else if (values[c] == 0 && (variant_candidates_at(v, values, c) & (1 << (d - 1))) != 0) {
                        places++;
                        where = c;
                    }
                }
                if (!placed && places == 1) {
                    values[where] = d;
                    progress = 1;
                }
            }
        }
        if (!progress) return 0;
    }
}

static void dig(Variant *v, int level) {
    const VariantShape *s = &v->shape;
    int *puzzle = v->puzzle, *order = v->order;
    for (int c = 0; c < s->size; c++) {
        puzzle[c] = v->full[c];
        order[c] = c;
    }
    shuffle(v, order, s->size);
    int keep = level == 0 ? (s->n == 9 ? 36 : 18) : 0;
    int clues = s->size;
    for (int i = 0; i < s->size && clues > keep; i++) {
        int c = order[i], d = puzzle[c];
        puzzle[c] = 0;
        int ok = variant_count_solutions(v, puzzle, 2) == 1 && (level == 2 || singlesSolve(v, puzzle));
        if (ok) clues--;
        else puzzle[c] = d;
    }
}

static void killerGivens(Variant *v, int level) {
    const VariantShape *s = &v->shape;
    int *puzzle = v->puzzle, *order = v->order;
    const int *full = v->full;
    for (int c = 0; c < s->size; c++) puzzle[c] = 0;
    for (int guard = 0; guard < s->size; guard++) {
        int n = variant_count_solutions(v, puzzle, 2);
        if (n == 1) break;
        int differ = -1;
        if (n == 2 && v->nodes < VARIANT_NODE_LIMIT) for (int c = 0; c < s->size && differ < 0; c++) if (puzzle[c] == 0 && v->found[c] != v->other[c]) differ = c;
        for (int c = 0; c < s->size && differ < 0; c++) if (puzzle[c] == 0) differ = c;
        puzzle[differ] = full[differ];
    }
    int extra = level == 0 ? 24 : level == 1 ? 10 : 0;
    for (int c = 0; c < s->size; c++) order[c] = c;
    shuffle(v, order, s->size);
    for (int i = 0, added = 0; i < s->size && added < extra; i++) {
        if (puzzle[order[i]] != 0) continue;
        puzzle[order[i]] = full[order[i]];
        added++;
    }
}

static void rememberKey(Variant *v, const int *givens) {
    const VariantShape *s = &v->shape;
    for (int c = 0; c < s->size; c++) {
        v->keyGivens[c] = givens[c];
        v->keyCages[c] = s->cageOf[c];
    }
    for (int k = 0; k < s->cageCount; k++) v->keySums[k] = s->cageSum[k];
    v->keyCount = s->cageCount;
    v->keyValid = 1;
}

static int sameKey(const Variant *v, const int *givens) {
    const VariantShape *s = &v->shape;
    if (!v->keyValid || v->keyCount != s->cageCount) return 0;
    for (int c = 0; c < s->size; c++) if (v->keyGivens[c] != givens[c] || v->keyCages[c] != s->cageOf[c]) return 0;
    for (int k = 0; k < s->cageCount; k++) if (v->keySums[k] != s->cageSum[k]) return 0;
    return 1;
}

void variant_generate(Variant *v, int level, int *out) {
    const VariantShape *s = &v->shape;
    for (;;) {
        fullGrid(v);
        if (s->kind == VARIANT_KIND_KILLER) {
            makeCages(v);
            killerGivens(v, level);
        } else {
            dig(v, level);
            if (level == 2 && singlesSolve(v, v->puzzle)) continue;
        }
        if (variant_count_solutions(v, v->puzzle, 2) != 1) continue;
        for (int c = 0; c < s->size; c++) v->solution[c] = v->full[c];
        rememberKey(v, v->puzzle);
        for (int c = 0; c < s->size; c++) out[c] = v->puzzle[c];
        return;
    }
}

int variant_hint(Variant *v, const int *values, const int *givens) {
    const VariantShape *s = &v->shape;
    if (!sameKey(v, givens)) {
        if (variant_count_solutions(v, givens, 2) != 1) return 0;
        for (int c = 0; c < s->size; c++) v->solution[c] = v->found[c];
        rememberKey(v, givens);
    }
    for (int c = 0; c < s->size; c++) {
        if (values[c] != 0) continue;
        int m = variant_candidates_at(v, values, c);
        if (m != 0 && (m & (m - 1)) == 0) {
            int d = __builtin_ctz((unsigned)m) + 1;
            if (d != v->solution[c]) continue;
            v->stepCell = c;
            v->stepDigit = d;
            v->stepUnit = VARIANT_UNIT_NAKED;
            v->hintTech = 0;
            return 1;
        }
    }
    for (int u = 0; u < s->unitCount; u++) {
        for (int d = 1; d <= s->n; d++) {
            int where = -1, places = 0, placed = 0;
            for (int k = 0; k < s->n; k++) {
                int c = s->unitCells[u * 9 + k];
                if (values[c] == d) placed = 1;
                else if (values[c] == 0 && (variant_candidates_at(v, values, c) & (1 << (d - 1))) != 0) {
                    places++;
                    where = c;
                }
            }
            if (placed || places != 1 || v->solution[where] != d) continue;
            v->stepCell = where;
            v->stepDigit = d;
            v->stepUnit = s->unitType[u];
            v->hintTech = 0;
            return 1;
        }
    }
    int best = -1, bestCount = 10;
    for (int c = 0; c < s->size; c++) {
        if (values[c] != 0) continue;
        int m = variant_candidates_at(v, values, c);
        int n = 0;
        for (int d = 0; d < s->n; d++) if ((m & (1 << d)) != 0) n++;
        if (n < bestCount) {
            best = c;
            bestCount = n;
        }
    }
    if (best < 0) return 0;
    v->stepCell = best;
    v->stepDigit = v->solution[best];
    v->stepUnit = VARIANT_UNIT_REVEAL;
    v->hintTech = 0;
    return 1;
}
