#include "Sudoku.h"
#include <string.h>

int SUDOKU_ROW[81];
int SUDOKU_COL[81];
int SUDOKU_BOX[81];
int SUDOKU_UNITS[27][9];
int SUDOKU_PEERS[81][20];
const int SUDOKU_TECH_BASE[SUDOKU_TECH_COUNT] = {10, 26, 30, 32, 42, 38, 44, 40, 41, 44, 45, 50, 54, 52, 46, 50, 56, 46, 47, 55, 46, 48, 47, 47, 48, 48, 56};
int SUDOKU_TECH_ORDER[SUDOKU_TECH_COUNT];
int SUDOKU_EXPERT_LIMIT;
static unsigned char SEE[6561];
static int tablesReady;

static void initTables(void) {
    if (tablesReady) return;
    for (int i = 0; i < 81; i++) {
        SUDOKU_ROW[i] = i / 9;
        SUDOKU_COL[i] = i % 9;
        SUDOKU_BOX[i] = (i / 27) * 3 + (i % 9) / 3;
    }
    for (int u = 0; u < 9; u++) {
        for (int k = 0; k < 9; k++) {
            SUDOKU_UNITS[u][k] = u * 9 + k;
            SUDOKU_UNITS[9 + u][k] = k * 9 + u;
            SUDOKU_UNITS[18 + u][k] = (u / 3) * 27 + (u % 3) * 3 + (k / 3) * 9 + k % 3;
        }
    }
    for (int i = 0; i < 81; i++) {
        int n = 0;
        for (int j = 0; j < 81; j++) {
            if (sudoku_sees(i, j)) SUDOKU_PEERS[i][n++] = j;
            SEE[i * 81 + j] = (unsigned char)sudoku_sees(i, j);
        }
    }
    int n = 0;
    for (int id = 0; id < 11; id++) SUDOKU_TECH_ORDER[n++] = id;
    for (int r = 0; r < 128; r++) {
        for (int id = 11; id < SUDOKU_TECH_COUNT; id++) if (SUDOKU_TECH_BASE[id] == r) SUDOKU_TECH_ORDER[n++] = id;
    }
    SUDOKU_EXPERT_LIMIT = 0;
    for (int id = 0; id < SUDOKU_TECH_COUNT; id++) if (SUDOKU_TECH_BASE[id] < SUDOKU_MASTER_RATING) SUDOKU_EXPERT_LIMIT++;
    tablesReady = 1;
}

int sudoku_sees(int a, int b) {
    return a != b && (a / 9 == b / 9 || a % 9 == b % 9 || (a / 27) * 3 + (a % 9) / 3 == (b / 27) * 3 + (b % 9) / 3);
}

int sudoku_bit(int digit) {
    return 1 << (digit - 1);
}

int sudoku_digit(int singleBit) {
    return __builtin_ctz(singleBit) + 1;
}

void sudoku_init(Sudoku *s, uint64_t seed) {
    initTables();
    memset(s, 0, sizeof(*s));
    s->techLimit = SUDOKU_TECH_COUNT;
    s->rng = seed ^ 0x9E3779B97F4A7C15ULL;
    if (s->rng == 0) s->rng = 1;
}

static uint32_t nextRandom(Sudoku *s) {
    uint64_t x = s->rng;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    s->rng = x;
    return (uint32_t)((x * 0x2545F4914F6CDD1DULL) >> 32);
}

static int nextInt(Sudoku *s, int bound) {
    return (int)(nextRandom(s) % (uint32_t)bound);
}

int sudoku_candidates(const int *values, int cell) {
    int used = 0;
    for (int k = 0; k < 20; k++) {
        int p = SUDOKU_PEERS[cell][k];
        if (values[p] != 0) used |= sudoku_bit(values[p]);
    }
    return SUDOKU_ALL & ~used;
}

static void search(Sudoku *s) {
    int best = -1;
    int bestMask = 0;
    int bestSize = 10;
    for (int i = 0; i < 81; i++) {
        if (s->work[i] != 0) continue;
        int m = sudoku_candidates(s->work, i);
        int n = __builtin_popcount(m);
        if (n == 0) return;
        if (n < bestSize) {
            bestSize = n;
            best = i;
            bestMask = m;
            if (n == 1) break;
        }
    }
    if (best < 0) {
        s->count++;
        if (!s->hasFound) {
            memcpy(s->found, s->work, sizeof(s->found));
            s->hasFound = 1;
        }
        return;
    }
    for (int m = bestMask; m != 0 && s->count < s->limit; m &= m - 1) {
        s->work[best] = sudoku_digit(m & -m);
        search(s);
    }
    s->work[best] = 0;
}

int sudoku_count_solutions(Sudoku *s, const int *puzzle, int max) {
    memcpy(s->work, puzzle, sizeof(s->work));
    s->count = 0;
    s->limit = max;
    s->hasFound = 0;
    search(s);
    return s->count;
}

static void shuffle(Sudoku *s, int *a, int n) {
    for (int k = n - 1; k > 0; k--) {
        int j = nextInt(s, k + 1);
        int t = a[k];
        a[k] = a[j];
        a[j] = t;
    }
}

static int fillRandom(Sudoku *s) {
    int best = -1;
    int bestMask = 0;
    int bestSize = 10;
    for (int i = 0; i < 81; i++) {
        if (s->work[i] != 0) continue;
        int m = sudoku_candidates(s->work, i);
        int n = __builtin_popcount(m);
        if (n == 0) return 0;
        if (n < bestSize) {
            bestSize = n;
            best = i;
            bestMask = m;
            if (n == 1) break;
        }
    }
    if (best < 0) return 1;
    int digits[9];
    int n = 0;
    for (int m = bestMask; m != 0; m &= m - 1) digits[n++] = sudoku_digit(m & -m);
    shuffle(s, digits, n);
    for (int k = 0; k < n; k++) {
        s->work[best] = digits[k];
        if (fillRandom(s)) return 1;
    }
    s->work[best] = 0;
    return 0;
}

void sudoku_full_grid(Sudoku *s, int *out) {
    memset(s->work, 0, sizeof(s->work));
    fillRandom(s);
    memcpy(out, s->work, sizeof(s->work));
}

static int keeps(Sudoku *s, const int *puzzle) {
    return sudoku_count_solutions(s, puzzle, 2) == 1 && (s->techLimit == SUDOKU_TECH_COUNT || sudoku_rate(s, puzzle) >= 0);
}

void sudoku_dig(Sudoku *s, const int *full, int minClues, int *out) {
    int puzzle[81];
    memcpy(puzzle, full, sizeof(puzzle));
    int order[41];
    for (int i = 0; i < 41; i++) order[i] = i;
    shuffle(s, order, 41);
    int clues = 81;
    for (int k = 0; k < 41 && clues > minClues; k++) {
        int a = order[k];
        int b = 80 - a;
        int va = puzzle[a];
        int vb = puzzle[b];
        puzzle[a] = 0;
        puzzle[b] = 0;
        if (keeps(s, puzzle)) {
            clues -= a == b ? 1 : 2;
        } else {
            puzzle[a] = va;
            puzzle[b] = vb;
        }
    }
    memcpy(out, puzzle, sizeof(puzzle));
}

void sudoku_load(Sudoku *s, const int *values) {
    memcpy(s->lv, values, sizeof(s->lv));
    for (int i = 0; i < 81; i++) s->lc[i] = s->lv[i] == 0 ? sudoku_candidates(s->lv, i) : 0;
}

int sudoku_complete(const Sudoku *s) {
    for (int i = 0; i < 81; i++) if (s->lv[i] == 0) return 0;
    return 1;
}

int sudoku_stuck(const Sudoku *s) {
    for (int i = 0; i < 81; i++) if (s->lv[i] == 0 && s->lc[i] == 0) return 1;
    return 0;
}

static void place(Sudoku *s, int cell, int d, int unit) {
    s->lv[cell] = d;
    s->lc[cell] = 0;
    int keep = ~sudoku_bit(d);
    for (int k = 0; k < 20; k++) s->lc[SUDOKU_PEERS[cell][k]] &= keep;
    s->stepCell = cell;
    s->stepDigit = d;
    s->stepUnit = unit;
}

static int singles(Sudoku *s) {
    for (int i = 0; i < 81; i++) {
        if (s->lv[i] == 0 && __builtin_popcount(s->lc[i]) == 1) {
            place(s, i, sudoku_digit(s->lc[i]), 3);
            return 1;
        }
    }
    for (int u = 0; u < 27; u++) {
        const int *cells = SUDOKU_UNITS[u];
        for (int d = 1; d <= 9; d++) {
            int b = sudoku_bit(d);
            int where = -1;
            int n = 0;
            for (int k = 0; k < 9; k++) {
                if ((s->lc[cells[k]] & b) != 0) {
                    n++;
                    where = cells[k];
                }
            }
            if (n == 1) {
                place(s, where, d, u / 9);
                return 1;
            }
        }
    }
    return 0;
}

static int rated(Sudoku *s, int r) {
    if (r > s->stepRating) s->stepRating = r;
    return 1;
}

static int drop(Sudoku *s, int cell, int mask) {
    if ((s->lc[cell] & mask) == 0) return 0;
    s->lc[cell] &= ~mask;
    return 1;
}

static int clearLineOutsideBox(Sudoku *s, const int *line, int box, int b) {
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        int c = line[k];
        if (SUDOKU_BOX[c] != box && (s->lc[c] & b) != 0) {
            s->lc[c] &= ~b;
            changed = 1;
        }
    }
    return changed;
}

static int lockedCandidates(Sudoku *s) {
    int changed = 0;
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int box = 0; box < 9; box++) {
            int rows = 0;
            int cols = 0;
            for (int k = 0; k < 9; k++) {
                int c = SUDOKU_UNITS[18 + box][k];
                if ((s->lc[c] & b) != 0) {
                    rows |= 1 << SUDOKU_ROW[c];
                    cols |= 1 << SUDOKU_COL[c];
                }
            }
            if (rows != 0 && __builtin_popcount(rows) == 1) {
                changed |= clearLineOutsideBox(s, SUDOKU_UNITS[__builtin_ctz(rows)], box, b);
            }
            if (cols != 0 && __builtin_popcount(cols) == 1) {
                changed |= clearLineOutsideBox(s, SUDOKU_UNITS[9 + __builtin_ctz(cols)], box, b);
            }
        }
        for (int line = 0; line < 18; line++) {
            int boxes = 0;
            for (int k = 0; k < 9; k++) {
                int c = SUDOKU_UNITS[line][k];
                if ((s->lc[c] & b) != 0) boxes |= 1 << SUDOKU_BOX[c];
            }
            if (boxes != 0 && __builtin_popcount(boxes) == 1) {
                int box = __builtin_ctz(boxes);
                for (int k = 0; k < 9; k++) {
                    int c = SUDOKU_UNITS[18 + box][k];
                    int inLine = line < 9 ? SUDOKU_ROW[c] == line : SUDOKU_COL[c] == line - 9;
                    if (!inLine && (s->lc[c] & b) != 0) {
                        s->lc[c] &= ~b;
                        changed = rated(s, 28);
                    }
                }
            }
        }
    }
    return changed;
}

static int clearOthers(Sudoku *s, const int *cells, int digits, int members) {
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        if ((members & (1 << k)) == 0 && (s->lc[cells[k]] & digits) != 0) {
            s->lc[cells[k]] &= ~digits;
            changed = 1;
        }
    }
    return changed;
}

static int keepOnly(Sudoku *s, const int *cells, int members, int digits) {
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        if ((members & (1 << k)) != 0 && (s->lc[cells[k]] & ~digits) != 0) {
            s->lc[cells[k]] &= digits;
            changed = 1;
        }
    }
    return changed;
}

static int subsets(Sudoku *s) {
    int changed = 0;
    for (int u = 0; u < 27; u++) {
        const int *cells = SUDOKU_UNITS[u];
        for (int a = 0; a < 9; a++) {
            int ma = s->lc[cells[a]];
            if (ma == 0 || __builtin_popcount(ma) > 3) continue;
            for (int b = a + 1; b < 9; b++) {
                int mb = s->lc[cells[b]];
                if (mb == 0 || __builtin_popcount(mb) > 3) continue;
                int m2 = ma | mb;
                if (__builtin_popcount(m2) == 2) {
                    changed |= clearOthers(s, cells, m2, (1 << a) | (1 << b));
                } else if (__builtin_popcount(m2) == 3) {
                    for (int c = b + 1; c < 9; c++) {
                        int mc = s->lc[cells[c]];
                        if (mc != 0 && (mc | m2) == m2) {
                            if (clearOthers(s, cells, m2, (1 << a) | (1 << b) | (1 << c))) changed = rated(s, 36);
                        }
                    }
                }
            }
        }
        for (int d = 1; d <= 9; d++) {
            int b = sudoku_bit(d);
            int m = 0;
            for (int k = 0; k < 9; k++) if ((s->lc[cells[k]] & b) != 0) m |= 1 << k;
            s->positions[d] = m;
        }
        for (int d1 = 1; d1 <= 9; d1++) {
            int p1 = s->positions[d1];
            if (p1 == 0 || __builtin_popcount(p1) > 3) continue;
            for (int d2 = d1 + 1; d2 <= 9; d2++) {
                int p2 = s->positions[d2];
                if (p2 == 0 || __builtin_popcount(p2) > 3) continue;
                int u2 = p1 | p2;
                if (__builtin_popcount(u2) == 2) {
                    if (keepOnly(s, cells, u2, sudoku_bit(d1) | sudoku_bit(d2))) changed = rated(s, 34);
                } else if (__builtin_popcount(u2) == 3) {
                    for (int d3 = d2 + 1; d3 <= 9; d3++) {
                        int p3 = s->positions[d3];
                        if (p3 != 0 && (p3 | u2) == u2) {
                            if (keepOnly(s, cells, u2, sudoku_bit(d1) | sudoku_bit(d2) | sudoku_bit(d3))) changed = rated(s, 40);
                        }
                    }
                }
            }
        }
    }
    return changed;
}

static int fishClear(Sudoku *s, int b, int t, int coverMask, int baseMask) {
    int changed = 0;
    for (int c = 0; c < 81; c++) {
        int base = t == 0 ? SUDOKU_ROW[c] : SUDOKU_COL[c];
        int cover = t == 0 ? SUDOKU_COL[c] : SUDOKU_ROW[c];
        if ((coverMask & (1 << cover)) != 0 && (baseMask & (1 << base)) == 0 && (s->lc[c] & b) != 0) {
            s->lc[c] &= ~b;
            changed = 1;
        }
    }
    return changed;
}

static int unitMask(const Sudoku *s, const int *cells, int b) {
    int m = 0;
    for (int k = 0; k < 9; k++) if ((s->lc[cells[k]] & b) != 0) m |= 1 << k;
    return m;
}

static void fillLineMasks(Sudoku *s, int b, int t) {
    for (int line = 0; line < 9; line++) s->lineMasks[line] = unitMask(s, SUDOKU_UNITS[t * 9 + line], b);
}

static int fish(Sudoku *s, int size) {
    int changed = 0;
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int t = 0; t < 2; t++) {
            fillLineMasks(s, b, t);
            for (int l1 = 0; l1 < 9; l1++) {
                int m1 = s->lineMasks[l1];
                if (m1 == 0 || __builtin_popcount(m1) > size) continue;
                for (int l2 = l1 + 1; l2 < 9; l2++) {
                    int m2 = s->lineMasks[l2];
                    if (m2 == 0 || __builtin_popcount(m2) > size) continue;
                    int u2 = m1 | m2;
                    if (size == 2) {
                        if (__builtin_popcount(u2) == 2) changed |= fishClear(s, b, t, u2, (1 << l1) | (1 << l2));
                    } else if (__builtin_popcount(u2) <= 3) {
                        for (int l3 = l2 + 1; l3 < 9; l3++) {
                            int m3 = s->lineMasks[l3];
                            if (m3 == 0 || __builtin_popcount(m3) > 3) continue;
                            int u3 = u2 | m3;
                            if (__builtin_popcount(u3) == 3) changed |= fishClear(s, b, t, u3, (1 << l1) | (1 << l2) | (1 << l3));
                        }
                    }
                }
            }
        }
    }
    return changed;
}

static int clearSeeing(Sudoku *s, int z, int c1, int c2, int c3) {
    int changed = 0;
    for (int c = 0; c < 81; c++) {
        if (c == c1 || c == c2 || c == c3 || (s->lc[c] & z) == 0) continue;
        if (!sudoku_sees(c, c1) || !sudoku_sees(c, c2) || (c3 >= 0 && !sudoku_sees(c, c3))) continue;
        s->lc[c] &= ~z;
        changed = 1;
    }
    return changed;
}

static int yWing(Sudoku *s) {
    for (int p = 0; p < 81; p++) {
        int pm = s->lc[p];
        if (__builtin_popcount(pm) != 2) continue;
        const int *peers = SUDOKU_PEERS[p];
        for (int a = 0; a < 20; a++) {
            int am = s->lc[peers[a]];
            if (__builtin_popcount(am) != 2 || am == pm || __builtin_popcount(am & pm) != 1) continue;
            for (int b = a + 1; b < 20; b++) {
                int bm = s->lc[peers[b]];
                if (__builtin_popcount(bm) != 2 || bm == pm || __builtin_popcount(bm & pm) != 1) continue;
                if ((am & pm) == (bm & pm)) continue;
                int z = am & bm & ~pm;
                if (__builtin_popcount(z) != 1) continue;
                if (clearSeeing(s, z, peers[a], peers[b], -1)) return 1;
            }
        }
    }
    return 0;
}

static int xyzWing(Sudoku *s) {
    for (int p = 0; p < 81; p++) {
        int pm = s->lc[p];
        if (__builtin_popcount(pm) != 3) continue;
        const int *peers = SUDOKU_PEERS[p];
        for (int a = 0; a < 20; a++) {
            int am = s->lc[peers[a]];
            if (__builtin_popcount(am) != 2 || (am & pm) != am) continue;
            for (int b = a + 1; b < 20; b++) {
                int bm = s->lc[peers[b]];
                if (__builtin_popcount(bm) != 2 || (bm & pm) != bm || bm == am) continue;
                int z = am & bm;
                if (__builtin_popcount(z) != 1) continue;
                if (clearSeeing(s, z, peers[a], peers[b], p)) return 1;
            }
        }
    }
    return 0;
}

static int skyscraper(Sudoku *s) {
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int t = 0; t < 2; t++) {
            fillLineMasks(s, b, t);
            for (int l1 = 0; l1 < 9; l1++) {
                int m1 = s->lineMasks[l1];
                if (__builtin_popcount(m1) != 2) continue;
                for (int l2 = l1 + 1; l2 < 9; l2++) {
                    int m2 = s->lineMasks[l2];
                    if (__builtin_popcount(m2) != 2 || __builtin_popcount(m1 & m2) != 1) continue;
                    int top1 = SUDOKU_UNITS[t * 9 + l1][__builtin_ctz(m1 & ~m2)];
                    int top2 = SUDOKU_UNITS[t * 9 + l2][__builtin_ctz(m2 & ~m1)];
                    if (clearSeeing(s, b, top1, top2, -1)) return 1;
                }
            }
        }
    }
    return 0;
}

static int twoStringKite(Sudoku *s) {
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int r = 0; r < 9; r++) {
            int rm = unitMask(s, SUDOKU_UNITS[r], b);
            if (__builtin_popcount(rm) != 2) continue;
            int r0 = SUDOKU_UNITS[r][__builtin_ctz(rm)];
            int r1 = SUDOKU_UNITS[r][__builtin_ctz(rm & (rm - 1))];
            for (int c = 0; c < 9; c++) {
                int cm = unitMask(s, SUDOKU_UNITS[9 + c], b);
                if (__builtin_popcount(cm) != 2) continue;
                int c0 = SUDOKU_UNITS[9 + c][__builtin_ctz(cm)];
                int c1 = SUDOKU_UNITS[9 + c][__builtin_ctz(cm & (cm - 1))];
                if (r0 == c0 || r0 == c1 || r1 == c0 || r1 == c1) continue;
                for (int i = 0; i < 2; i++) {
                    int inBox = i == 0 ? r0 : r1;
                    int rowEnd = i == 0 ? r1 : r0;
                    for (int j = 0; j < 2; j++) {
                        int boxMate = j == 0 ? c0 : c1;
                        int colEnd = j == 0 ? c1 : c0;
                        if (SUDOKU_BOX[inBox] != SUDOKU_BOX[boxMate]) continue;
                        if (clearSeeing(s, b, rowEnd, colEnd, -1)) return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int wWing(Sudoku *s) {
    for (int p = 0; p < 81; p++) {
        int m = s->lc[p];
        if (__builtin_popcount(m) != 2) continue;
        for (int q = p + 1; q < 81; q++) {
            if (s->lc[q] != m || sudoku_sees(p, q)) continue;
            for (int rest = m; rest != 0; rest &= rest - 1) {
                int b = rest & -rest;
                for (int u = 0; u < 27; u++) {
                    int um = unitMask(s, SUDOKU_UNITS[u], b);
                    if (__builtin_popcount(um) != 2) continue;
                    int e1 = SUDOKU_UNITS[u][__builtin_ctz(um)];
                    int e2 = SUDOKU_UNITS[u][__builtin_ctz(um & (um - 1))];
                    if (!(sudoku_sees(e1, p) && sudoku_sees(e2, q)) && !(sudoku_sees(e1, q) && sudoku_sees(e2, p))) continue;
                    if (clearSeeing(s, m & ~b, p, q, -1)) return 1;
                }
            }
        }
    }
    return 0;
}

static int uniqueRectangle(Sudoku *s) {
    int corners[4];
    for (int r1 = 0; r1 < 9; r1++) {
        for (int r2 = r1 + 1; r2 < 9; r2++) {
            int sameBand = r1 / 3 == r2 / 3;
            for (int c1 = 0; c1 < 9; c1++) {
                for (int c2 = c1 + 1; c2 < 9; c2++) {
                    if (sameBand == (c1 / 3 == c2 / 3)) continue;
                    corners[0] = r1 * 9 + c1;
                    corners[1] = r1 * 9 + c2;
                    corners[2] = r2 * 9 + c2;
                    corners[3] = r2 * 9 + c1;
                    for (int k = 0; k < 4; k++) {
                        int target = corners[k];
                        int m = s->lc[corners[(k + 1) & 3]];
                        if (__builtin_popcount(m) != 2 || s->lc[corners[(k + 2) & 3]] != m || s->lc[corners[(k + 3) & 3]] != m) continue;
                        if ((s->lc[target] & m) != m || s->lc[target] == m) continue;
                        s->lc[target] &= ~m;
                        return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int dropOutside(Sudoku *s, const int *cells, int digits, int members) {
    int changed = 0;
    for (int k = 0; k < 9; k++) if ((members & (1 << k)) == 0 && drop(s, cells[k], digits)) changed = 1;
    return changed;
}

static int keepInside(Sudoku *s, const int *cells, int members, int digits) {
    int changed = 0;
    for (int k = 0; k < 9; k++) if ((members & (1 << k)) != 0 && drop(s, cells[k], SUDOKU_ALL & ~digits)) changed = 1;
    return changed;
}

static int nakedQuad(Sudoku *s) {
    for (int u = 0; u < 27; u++) {
        const int *cells = SUDOKU_UNITS[u];
        for (int a = 0; a < 9; a++) {
            int ma = s->lc[cells[a]];
            if (ma == 0 || __builtin_popcount(ma) > 4) continue;
            for (int b = a + 1; b < 9; b++) {
                int mb = s->lc[cells[b]];
                int m2 = ma | mb;
                if (mb == 0 || __builtin_popcount(m2) > 4) continue;
                for (int c = b + 1; c < 9; c++) {
                    int mc = s->lc[cells[c]];
                    int m3 = m2 | mc;
                    if (mc == 0 || __builtin_popcount(m3) > 4) continue;
                    for (int d = c + 1; d < 9; d++) {
                        int md = s->lc[cells[d]];
                        int m4 = m3 | md;
                        if (md == 0 || __builtin_popcount(m4) != 4) continue;
                        if (dropOutside(s, cells, m4, (1 << a) | (1 << b) | (1 << c) | (1 << d))) return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int hiddenQuad(Sudoku *s) {
    for (int u = 0; u < 27; u++) {
        const int *cells = SUDOKU_UNITS[u];
        for (int d = 1; d <= 9; d++) s->positions[d] = unitMask(s, cells, sudoku_bit(d));
        for (int d1 = 1; d1 <= 9; d1++) {
            int p1 = s->positions[d1];
            if (p1 == 0 || __builtin_popcount(p1) > 4) continue;
            for (int d2 = d1 + 1; d2 <= 9; d2++) {
                int p2 = s->positions[d2];
                int u2 = p1 | p2;
                if (p2 == 0 || __builtin_popcount(u2) > 4) continue;
                for (int d3 = d2 + 1; d3 <= 9; d3++) {
                    int p3 = s->positions[d3];
                    int u3 = u2 | p3;
                    if (p3 == 0 || __builtin_popcount(u3) > 4) continue;
                    for (int d4 = d3 + 1; d4 <= 9; d4++) {
                        int p4 = s->positions[d4];
                        int u4 = u3 | p4;
                        if (p4 == 0 || __builtin_popcount(u4) != 4) continue;
                        if (keepInside(s, cells, u4, sudoku_bit(d1) | sudoku_bit(d2) | sudoku_bit(d3) | sudoku_bit(d4))) return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int baseUnion(const Sudoku *s, int base) {
    int all = 0;
    for (int l = 0; l < 9; l++) {
        if ((base & (1 << l)) == 0) continue;
        if (s->lineMasks[l] == 0) return 0;
        all |= s->lineMasks[l];
    }
    return all;
}

static int fishDrop(Sudoku *s, int b, int t, int cover, int base) {
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        if ((cover & (1 << k)) == 0) continue;
        for (int l = 0; l < 9; l++) if ((base & (1 << l)) == 0 && drop(s, SUDOKU_UNITS[t * 9 + l][k], b)) changed = 1;
    }
    return changed;
}

static int jellyfish(Sudoku *s) {
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int t = 0; t < 2; t++) {
            fillLineMasks(s, b, t);
            for (int base = 0; base < 512; base++) {
                if (__builtin_popcount(base) != 4) continue;
                int cover = baseUnion(s, base);
                if (__builtin_popcount(cover) == 4 && fishDrop(s, b, t, cover, base)) return 1;
            }
        }
    }
    return 0;
}

static int finnedDrop(Sudoku *s, int b, int t, int base, int cover) {
    int finBox = -1;
    for (int l = 0; l < 9; l++) {
        if ((base & (1 << l)) == 0) continue;
        int fins = s->lineMasks[l] & ~cover;
        if (fins == 0) continue;
        int box = SUDOKU_BOX[SUDOKU_UNITS[t * 9 + l][__builtin_ctz(fins)]];
        if (finBox >= 0 && box != finBox) return 0;
        finBox = box;
    }
    if (finBox < 0) return 0;
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        if ((cover & (1 << k)) == 0) continue;
        for (int l = 0; l < 9; l++) {
            if ((base & (1 << l)) != 0) continue;
            int c = SUDOKU_UNITS[t * 9 + l][k];
            if (SUDOKU_BOX[c] == finBox && drop(s, c, b)) changed = 1;
        }
    }
    return changed;
}

static int finnedFish(Sudoku *s, int n) {
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int t = 0; t < 2; t++) {
            fillLineMasks(s, b, t);
            for (int base = 0; base < 512; base++) {
                if (__builtin_popcount(base) != n) continue;
                int all = baseUnion(s, base);
                if (__builtin_popcount(all) <= n) continue;
                for (int st = 0; st < 3; st++) {
                    int block = 7 << (3 * st);
                    int outside = all & ~block;
                    if (__builtin_popcount(outside) > n) continue;
                    int inside = all & block;
                    for (int sub = inside; ; sub = (sub - 1) & inside) {
                        int cover = outside | sub;
                        if (__builtin_popcount(cover) == n && finnedDrop(s, b, t, base, cover)) return 1;
                        if (sub == 0) break;
                    }
                }
            }
        }
    }
    return 0;
}

static int emptyRectangle(Sudoku *s) {
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        for (int box = 0; box < 9; box++) {
            const int *cells = SUDOKU_UNITS[18 + box];
            int n = 0;
            for (int k = 0; k < 9; k++) if ((s->lc[cells[k]] & b) != 0) n++;
            if (n < 2) continue;
            int band = box / 3;
            int stack = box % 3;
            for (int i = 0; i < 3; i++) {
                int row = band * 3 + i;
                for (int j = 0; j < 3; j++) {
                    int col = stack * 3 + j;
                    int cross = 1;
                    for (int k = 0; k < 9 && cross; k++) {
                        int c = cells[k];
                        if ((s->lc[c] & b) != 0 && SUDOKU_ROW[c] != row && SUDOKU_COL[c] != col) cross = 0;
                    }
                    if (!cross) continue;
                    for (int line = 0; line < 9; line++) {
                        if (line / 3 == stack) continue;
                        int m = unitMask(s, SUDOKU_UNITS[9 + line], b);
                        if (__builtin_popcount(m) != 2 || (m & (1 << row)) == 0) continue;
                        int far = __builtin_ctz(m & ~(1 << row));
                        if (far / 3 != band && drop(s, far * 9 + col, b)) return 1;
                    }
                    for (int line = 0; line < 9; line++) {
                        if (line / 3 == band) continue;
                        int m = unitMask(s, SUDOKU_UNITS[line], b);
                        if (__builtin_popcount(m) != 2 || (m & (1 << col)) == 0) continue;
                        int far = __builtin_ctz(m & ~(1 << col));
                        if (far / 3 != stack && drop(s, row * 9 + far, b)) return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int remotePair(Sudoku *s) {
    for (int p = 0; p < 81; p++) {
        int m = s->lc[p];
        if (__builtin_popcount(m) != 2) continue;
        for (int i = 0; i < 81; i++) s->dist81[i] = -1;
        s->dist81[p] = 0;
        int head = 0;
        int tail = 0;
        s->queue81[tail++] = p;
        while (head < tail) {
            int x = s->queue81[head++];
            for (int k = 0; k < 20; k++) {
                int y = SUDOKU_PEERS[x][k];
                if (s->lc[y] != m || s->dist81[y] >= 0) continue;
                s->dist81[y] = s->dist81[x] + 1;
                s->queue81[tail++] = y;
            }
        }
        for (int k = 1; k < tail; k++) {
            int q = s->queue81[k];
            if (s->dist81[q] < 3 || (s->dist81[q] & 1) == 0) continue;
            int changed = 0;
            for (int c = 0; c < 81; c++) if (c != p && c != q && SEE[c * 81 + p] && SEE[c * 81 + q] && drop(s, c, m)) changed = 1;
            if (changed) return 1;
        }
    }
    return 0;
}

static int wingDrop(Sudoku *s, int unionMask) {
    const int *quad = s->quad;
    int z = 0;
    for (int rest = unionMask; rest != 0; rest &= rest - 1) {
        int x = rest & -rest;
        int restricted = 1;
        for (int i = 0; i < 4 && restricted; i++) {
            if ((s->lc[quad[i]] & x) == 0) continue;
            for (int j = i + 1; j < 4; j++) if ((s->lc[quad[j]] & x) != 0 && !SEE[quad[i] * 81 + quad[j]]) restricted = 0;
        }
        if (restricted) continue;
        if (z != 0) return 0;
        z = x;
    }
    if (z == 0) return 0;
    int changed = 0;
    for (int t = 0; t < 81; t++) {
        if ((s->lc[t] & z) == 0 || t == quad[0] || t == quad[1] || t == quad[2] || t == quad[3]) continue;
        int all = 1;
        for (int i = 0; i < 4; i++) if ((s->lc[quad[i]] & z) != 0 && !SEE[t * 81 + quad[i]]) all = 0;
        if (all && drop(s, t, z)) changed = 1;
    }
    return changed;
}

static int wxyzWing(Sudoku *s) {
    int n = 0;
    for (int c = 0; c < 81; c++) if (__builtin_popcount(s->lc[c]) >= 2 && __builtin_popcount(s->lc[c]) <= 4) s->cellList[n++] = c;
    for (int a = 0; a < n; a++) {
        int ma = s->lc[s->cellList[a]];
        for (int b = a + 1; b < n; b++) {
            int m2 = ma | s->lc[s->cellList[b]];
            if (__builtin_popcount(m2) > 4) continue;
            for (int c = b + 1; c < n; c++) {
                int m3 = m2 | s->lc[s->cellList[c]];
                if (__builtin_popcount(m3) > 4) continue;
                for (int d = c + 1; d < n; d++) {
                    int m4 = m3 | s->lc[s->cellList[d]];
                    if (__builtin_popcount(m4) != 4) continue;
                    s->quad[0] = s->cellList[a];
                    s->quad[1] = s->cellList[b];
                    s->quad[2] = s->cellList[c];
                    s->quad[3] = s->cellList[d];
                    if (wingDrop(s, m4)) return 1;
                }
            }
        }
    }
    return 0;
}

static int dropSeeingCorners(Sudoku *s, int digits, int members) {
    const int *corners = s->corners;
    int changed = 0;
    for (int t = 0; t < 81; t++) {
        if ((s->lc[t] & digits) == 0 || t == corners[0] || t == corners[1] || t == corners[2] || t == corners[3]) continue;
        int all = 1;
        for (int i = 0; i < 4; i++) if ((members & (1 << i)) != 0 && !SEE[t * 81 + corners[i]]) all = 0;
        if (all && drop(s, t, digits)) changed = 1;
    }
    return changed;
}

static int urExtra(Sudoku *s, int diagonal) {
    const int *corners = s->corners;
    for (int k = 0; k < 4; k++) {
        int m = s->lc[corners[k]];
        if (__builtin_popcount(m) != 2) continue;
        int floors = 0;
        int roofs = 0;
        int extra = 0;
        for (int i = 0; i < 4; i++) {
            int v = s->lc[corners[i]];
            if (v == m) {
                floors |= 1 << i;
            } else if ((v & m) == m && __builtin_popcount(v) == 3 && (extra == 0 || (v & ~m) == extra)) {
                extra = v & ~m;
                roofs |= 1 << i;
            }
        }
        if ((floors | roofs) != 15 || extra == 0 || __builtin_popcount(floors) == 3) continue;
        int adjacent = __builtin_popcount(floors) == 2 && floors != 5 && floors != 10;
        if (adjacent != diagonal && dropSeeingCorners(s, extra, roofs)) return 1;
    }
    return 0;
}

static int urSubset(Sudoku *s, const int *cells, int r1, int r2, int extra) {
    int n = 0;
    for (int k = 0; k < 9; k++) {
        int c = cells[k];
        if (c != r1 && c != r2 && s->lc[c] != 0) s->others[n++] = c;
    }
    for (int sub = 1; sub < (1 << n); sub++) {
        int size = __builtin_popcount(sub);
        if (size > 3) continue;
        int m = extra;
        for (int i = 0; i < n; i++) if ((sub & (1 << i)) != 0) m |= s->lc[s->others[i]];
        if (__builtin_popcount(m) != size + 1) continue;
        int changed = 0;
        for (int i = 0; i < n; i++) if ((sub & (1 << i)) == 0 && drop(s, s->others[i], m)) changed = 1;
        if (changed) return 1;
    }
    return 0;
}

static int urType3(Sudoku *s) {
    const int *corners = s->corners;
    for (int k = 0; k < 4; k++) {
        int m = s->lc[corners[k]];
        if (__builtin_popcount(m) != 2 || s->lc[corners[(k + 1) & 3]] != m) continue;
        int r1 = corners[(k + 2) & 3];
        int r2 = corners[(k + 3) & 3];
        if ((s->lc[r1] & m) != m || (s->lc[r2] & m) != m || s->lc[r1] == m || s->lc[r2] == m) continue;
        int extra = (s->lc[r1] | s->lc[r2]) & ~m;
        int line = SUDOKU_ROW[r1] == SUDOKU_ROW[r2] ? SUDOKU_ROW[r1] : 9 + SUDOKU_COL[r1];
        if (urSubset(s, SUDOKU_UNITS[line], r1, r2, extra)) return 1;
        if (SUDOKU_BOX[r1] == SUDOKU_BOX[r2] && urSubset(s, SUDOKU_UNITS[18 + SUDOKU_BOX[r1]], r1, r2, extra)) return 1;
    }
    return 0;
}

static int onlyIn(const Sudoku *s, const int *cells, int x, int a, int b) {
    for (int k = 0; k < 9; k++) {
        int c = cells[k];
        if ((s->lc[c] & x) != 0 && c != a && c != b) return 0;
    }
    return 1;
}

static int urType4(Sudoku *s) {
    const int *corners = s->corners;
    for (int k = 0; k < 4; k++) {
        int m = s->lc[corners[k]];
        if (__builtin_popcount(m) != 2 || s->lc[corners[(k + 1) & 3]] != m) continue;
        int r1 = corners[(k + 2) & 3];
        int r2 = corners[(k + 3) & 3];
        if ((s->lc[r1] & m) != m || (s->lc[r2] & m) != m || (s->lc[r1] == m && s->lc[r2] == m)) continue;
        const int *line = SUDOKU_ROW[r1] == SUDOKU_ROW[r2] ? SUDOKU_UNITS[SUDOKU_ROW[r1]] : SUDOKU_UNITS[9 + SUDOKU_COL[r1]];
        const int *box = SUDOKU_UNITS[18 + SUDOKU_BOX[r1]];
        for (int rest = m; rest != 0; rest &= rest - 1) {
            int x = rest & -rest;
            int y = m & ~x;
            if (!onlyIn(s, line, x, r1, r2) && !(SUDOKU_BOX[r1] == SUDOKU_BOX[r2] && onlyIn(s, box, x, r1, r2))) continue;
            int a = drop(s, r1, y);
            int b = drop(s, r2, y);
            if (a || b) return 1;
        }
    }
    return 0;
}

static int urType6(Sudoku *s) {
    const int *corners = s->corners;
    for (int k = 0; k < 2; k++) {
        int m = s->lc[corners[k]];
        if (__builtin_popcount(m) != 2 || s->lc[corners[k + 2]] != m) continue;
        int g1 = corners[k + 1];
        int g2 = corners[(k + 3) & 3];
        if ((s->lc[g1] & m) != m || (s->lc[g2] & m) != m || s->lc[g1] == m || s->lc[g2] == m) continue;
        for (int rest = m; rest != 0; rest &= rest - 1) {
            int x = rest & -rest;
            int rows = onlyIn(s, SUDOKU_UNITS[SUDOKU_ROW[corners[0]]], x, corners[0], corners[1]) && onlyIn(s, SUDOKU_UNITS[SUDOKU_ROW[corners[2]]], x, corners[2], corners[3]);
            int cols = onlyIn(s, SUDOKU_UNITS[9 + SUDOKU_COL[corners[0]]], x, corners[0], corners[3]) && onlyIn(s, SUDOKU_UNITS[9 + SUDOKU_COL[corners[1]]], x, corners[1], corners[2]);
            if (!rows && !cols) continue;
            int a = drop(s, g1, x);
            int b = drop(s, g2, x);
            if (a || b) return 1;
        }
    }
    return 0;
}

static int hiddenRectangle(Sudoku *s) {
    const int *corners = s->corners;
    for (int k = 0; k < 4; k++) {
        int a = corners[k];
        int m = s->lc[a];
        if (__builtin_popcount(m) != 2) continue;
        if ((s->lc[corners[0]] & m) != m || (s->lc[corners[1]] & m) != m || (s->lc[corners[2]] & m) != m || (s->lc[corners[3]] & m) != m) continue;
        int d = corners[(k + 2) & 3];
        for (int rest = m; rest != 0; rest &= rest - 1) {
            int x = rest & -rest;
            int y = m & ~x;
            if (!onlyIn(s, SUDOKU_UNITS[SUDOKU_ROW[d]], x, d, SUDOKU_ROW[d] * 9 + SUDOKU_COL[a])) continue;
            if (!onlyIn(s, SUDOKU_UNITS[9 + SUDOKU_COL[d]], x, d, SUDOKU_ROW[a] * 9 + SUDOKU_COL[d])) continue;
            if (drop(s, d, y)) return 1;
        }
    }
    return 0;
}

static int rectangle(Sudoku *s, int kind) {
    switch (kind) {
        case 2: return urExtra(s, 0);
        case 3: return urType3(s);
        case 4: return urType4(s);
        case 5: return urExtra(s, 1);
        case 6: return urType6(s);
        default: return hiddenRectangle(s);
    }
}

static int rectangles(Sudoku *s, int kind) {
    for (int r1 = 0; r1 < 9; r1++) {
        for (int r2 = r1 + 1; r2 < 9; r2++) {
            int sameBand = r1 / 3 == r2 / 3;
            for (int c1 = 0; c1 < 9; c1++) {
                for (int c2 = c1 + 1; c2 < 9; c2++) {
                    if (sameBand == (c1 / 3 == c2 / 3)) continue;
                    s->corners[0] = r1 * 9 + c1;
                    s->corners[1] = r1 * 9 + c2;
                    s->corners[2] = r2 * 9 + c2;
                    s->corners[3] = r2 * 9 + c1;
                    if (rectangle(s, kind)) return 1;
                }
            }
        }
    }
    return 0;
}

static int bugPlusOne(Sudoku *s) {
    int tri = -1;
    for (int c = 0; c < 81; c++) {
        int n = __builtin_popcount(s->lc[c]);
        if (n == 0 || n == 2) continue;
        if (n != 3 || tri >= 0) return 0;
        tri = c;
    }
    if (tri < 0) return 0;
    for (int rest = s->lc[tri]; rest != 0; rest &= rest - 1) {
        int x = rest & -rest;
        int ok = 1;
        for (int u = 0; u < 27 && ok; u++) {
            const int *cells = SUDOKU_UNITS[u];
            int home = u == SUDOKU_ROW[tri] || u == 9 + SUDOKU_COL[tri] || u == 18 + SUDOKU_BOX[tri];
            for (int b = 1; b < 512 && ok; b <<= 1) {
                int n = home && b == x ? -1 : 0;
                for (int k = 0; k < 9; k++) if ((s->lc[cells[k]] & b) != 0) n++;
                if (n != 0 && n != 2) ok = 0;
            }
        }
        if (ok) {
            s->lc[tri] = x;
            return 1;
        }
    }
    return 0;
}

static int apply(Sudoku *s, int id) {
    switch (id) {
        case 0: return singles(s);
        case 1: return lockedCandidates(s);
        case 2: return subsets(s);
        case 3: return fish(s, 2);
        case 4: return yWing(s);
        case 5: return fish(s, 3);
        case 6: return xyzWing(s);
        case 7: return skyscraper(s);
        case 8: return twoStringKite(s);
        case 9: return wWing(s);
        case 10: return uniqueRectangle(s);
        case 11: return nakedQuad(s);
        case 12: return hiddenQuad(s);
        case 13: return jellyfish(s);
        case 14: return finnedFish(s, 2);
        case 15: return finnedFish(s, 3);
        case 16: return finnedFish(s, 4);
        case 17: return emptyRectangle(s);
        case 18: return remotePair(s);
        case 19: return wxyzWing(s);
        case 20: return rectangles(s, 2);
        case 21: return rectangles(s, 3);
        case 22: return rectangles(s, 4);
        case 23: return rectangles(s, 5);
        case 24: return rectangles(s, 6);
        case 25: return rectangles(s, 7);
        case 26: return bugPlusOne(s);
    }
    return 0;
}

int sudoku_step(Sudoku *s) {
    for (int k = 0; k < s->techLimit; k++) {
        int id = SUDOKU_TECH_ORDER[k];
        s->stepRating = SUDOKU_TECH_BASE[id];
        if (apply(s, id)) {
            s->stepOrder = k;
            return id;
        }
    }
    return -1;
}

int sudoku_rate(Sudoku *s, const int *puzzle) {
    sudoku_load(s, puzzle);
    int max = 0;
    s->rateOrder = 0;
    s->rateTech = 0;
    while (!sudoku_complete(s)) {
        if (sudoku_stuck(s)) return -1;
        int t = sudoku_step(s);
        if (t < 0) return -1;
        if (s->stepRating > max) {
            max = s->stepRating;
            s->rateTech = t;
        }
        if (s->stepOrder > s->rateOrder) s->rateOrder = s->stepOrder;
    }
    return max;
}

static void noteHint(Sudoku *s, int t) {
    if (s->stepRating <= s->hintRating) return;
    s->hintRating = s->stepRating;
    s->hintTech = t;
}

int sudoku_hint(Sudoku *s, const int *values, const int *givens) {
    sudoku_load(s, values);
    s->hintTech = 0;
    s->hintRating = 0;
    while (!sudoku_complete(s) && !sudoku_stuck(s)) {
        int t = sudoku_step(s);
        if (t < 0) break;
        if (t == 0) return 1;
        noteHint(s, t);
    }
    sudoku_load(s, givens);
    s->hintTech = 0;
    s->hintRating = 0;
    while (!sudoku_complete(s) && !sudoku_stuck(s)) {
        int t = sudoku_step(s);
        if (t < 0) return 0;
        if (t > 0) noteHint(s, t);
        if (t == 0 && values[s->stepCell] == 0) return 1;
    }
    return 0;
}

void sudoku_generate(Sudoku *s, int level, int *puzzle) {
    int limit = level < 2 ? 1 : level == 2 ? 3 : level == 3 ? SUDOKU_EXPERT_LIMIT : SUDOKU_TECH_COUNT;
    int minClues = level == 0 ? 38 : 0;
    int full[81];
    for (;;) {
        s->techLimit = limit;
        sudoku_full_grid(s, full);
        sudoku_dig(s, full, minClues, puzzle);
        int r = sudoku_rate(s, puzzle);
        s->techLimit = SUDOKU_TECH_COUNT;
        int ok = r >= 0 && (level < 2 || (level == 2 ? s->rateOrder >= 1 : level == 3 ? s->rateOrder >= 3 : r >= SUDOKU_MASTER_RATING));
        if (ok) {
            memcpy(s->solution, full, sizeof(s->solution));
            return;
        }
    }
}
