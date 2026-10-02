#include "Sudoku.h"
#include <string.h>

int SUDOKU_ROW[81];
int SUDOKU_COL[81];
int SUDOKU_BOX[81];
int SUDOKU_UNITS[27][9];
int SUDOKU_PEERS[81][20];
const int SUDOKU_TECH_BASE[SUDOKU_TECH_COUNT] = {10, 26, 30, 32, 42, 38, 44, 40, 41, 44, 45, 50, 54, 52, 46, 50, 56, 46, 47, 55, 46, 48, 47, 47, 48, 48, 56, 65, 66, 68, 70, 73, 70, 75, 78, 80, 82, 84, 85, 86, 90, 95};
int SUDOKU_TECH_ORDER[SUDOKU_TECH_COUNT];
int SUDOKU_EXPERT_LIMIT;
static unsigned char SEE[6561];
static int PEER_SET[243];
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
    for (int c = 0; c < 81; c++) {
        for (int k = 0; k < 20; k++) {
            int p = SUDOKU_PEERS[c][k];
            PEER_SET[c * 3 + p / 27] |= 1 << (p % 27);
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

static int positionIn(int c, int u) {
    return u < 9 ? SUDOKU_COL[c] : u < 18 ? SUDOKU_ROW[c] : (SUDOKU_ROW[c] % 3) * 3 + SUDOKU_COL[c] % 3;
}

static int chainBonus(int links) {
    if (links <= 4) return 0;
    int bonus = (links - 4) >> 1;
    return bonus < 10 ? bonus : 10;
}

static void prepareUnits(Sudoku *s) {
    for (int u = 0; u < 27; u++) {
        for (int d = 1; d <= 9; d++) s->unitPos[u * 9 + d - 1] = unitMask(s, SUDOKU_UNITS[u], sudoku_bit(d));
    }
    memset(s->digitCells, 0, sizeof(s->digitCells));
    for (int c = 0; c < 81; c++) {
        for (int rest = s->lc[c]; rest != 0; rest &= rest - 1) s->digitCells[__builtin_ctz(rest) * 3 + c / 27] |= 1 << (c % 27);
    }
}

static void prepareGroups(Sudoku *s) {
    int n = 0;
    for (int i = 0; i < 486; i++) s->groupAt[i] = -1;
    for (int d = 1; d <= 9; d++) {
        int b = sudoku_bit(d);
        s->groupFirst[d] = n;
        for (int box = 0; box < 9; box++) {
            int top = (box / 3) * 3;
            int left = (box % 3) * 3;
            for (int seg = 0; seg < 6; seg++) {
                int size = 0;
                for (int k = 0; k < 3; k++) {
                    int c = seg < 3 ? (top + seg) * 9 + left + k : (top + k) * 9 + left + seg - 3;
                    if ((s->lc[c] & b) != 0) s->groupCells[n * 3 + size++] = c;
                }
                if (size < 2) continue;
                s->groupDigit[n] = d;
                s->groupSize[n] = size;
                s->groupBox[n] = box;
                s->groupLine[n] = seg < 3 ? top + seg : 9 + left + seg - 3;
                for (int w = 0; w < 3; w++) {
                    int seen = -1;
                    for (int k = 0; k < size; k++) seen &= PEER_SET[s->groupCells[n * 3 + k] * 3 + w];
                    s->groupSeen[n * 3 + w] = seen;
                }
                s->groupAt[(d - 1) * 54 + box * 6 + seg] = n;
                n++;
            }
        }
    }
    s->groupFirst[10] = n;
    s->groupCount = n;
}

static int restNode(const Sudoku *s, int u, int d, int rest, int grouped) {
    if (rest == 0) return -1;
    const int *cells = SUDOKU_UNITS[u];
    if (__builtin_popcount(rest) == 1) return cells[__builtin_ctz(rest)] * 9 + d - 1;
    if (!grouped) return -1;
    int first = cells[__builtin_ctz(rest)];
    int sameBox = 1;
    int sameRow = 1;
    int sameCol = 1;
    for (int m = rest & (rest - 1); m != 0; m &= m - 1) {
        int c = cells[__builtin_ctz(m)];
        if (SUDOKU_BOX[c] != SUDOKU_BOX[first]) sameBox = 0;
        if (SUDOKU_ROW[c] != SUDOKU_ROW[first]) sameRow = 0;
        if (SUDOKU_COL[c] != SUDOKU_COL[first]) sameCol = 0;
    }
    if (!sameBox || (!sameRow && !sameCol)) return -1;
    int g = s->groupAt[(d - 1) * 54 + SUDOKU_BOX[first] * 6 + (sameRow ? SUDOKU_ROW[first] % 3 : 3 + SUDOKU_COL[first] % 3)];
    return g >= 0 && s->groupSize[g] == __builtin_popcount(rest) ? 729 + g : -1;
}

static int addLink(Sudoku *s, int n, int state) {
    if (n >= SUDOKU_MAX_LINKS) return n;
    s->linkTo[n] = state;
    return n + 1;
}

static int linked(const Sudoku *s, int from, int to, int state) {
    for (int k = from; k < to; k++) if (s->linkTo[k] == state) return 1;
    return 0;
}

static int seesGroup(const Sudoku *s, int c, int g) {
    return (s->groupSeen[g * 3 + c / 27] & (1 << (c % 27))) != 0;
}

static int groupWithin(const Sudoku *s, int h, int g) {
    for (int i = 0; i < s->groupSize[h]; i++) if (!seesGroup(s, s->groupCells[h * 3 + i], g)) return 0;
    return 1;
}

static void buildLinks(Sudoku *s, int id) {
    int grouped = id == 31;
    int units = id != 28;
    int bivalue = id != 27;
    int mates = id >= 29;
    int n = 0;
    for (int node = 0; node < SUDOKU_NODES; node++) {
        s->linkStart[node * 2] = n;
        if (node < 729) {
            int c = node / 9;
            int d = node % 9 + 1;
            int b = sudoku_bit(d);
            int alive = (s->lc[c] & b) != 0;
            if (alive && units) {
                int first = n;
                for (int k = 0; k < 3; k++) {
                    int u = k == 0 ? SUDOKU_ROW[c] : k == 1 ? 9 + SUDOKU_COL[c] : 18 + SUDOKU_BOX[c];
                    int target = restNode(s, u, d, s->unitPos[u * 9 + d - 1] & ~(1 << positionIn(c, u)), grouped);
                    if (target >= 0 && !linked(s, first, n, target * 2 + 1)) n = addLink(s, n, target * 2 + 1);
                }
            }
            if (alive && bivalue && __builtin_popcount(s->lc[c]) == 2) n = addLink(s, n, (c * 9 + __builtin_ctz(s->lc[c] & ~b)) * 2 + 1);
            s->linkStart[node * 2 + 1] = n;
            if (!alive) continue;
            for (int k = 0; k < 20; k++) {
                int p = SUDOKU_PEERS[c][k];
                if ((s->lc[p] & b) != 0) n = addLink(s, n, (p * 9 + d - 1) * 2);
            }
            if (mates) {
                for (int rest = s->lc[c] & ~b; rest != 0; rest &= rest - 1) n = addLink(s, n, (c * 9 + __builtin_ctz(rest)) * 2);
            }
            if (grouped) {
                for (int g = s->groupFirst[d]; g < s->groupFirst[d + 1]; g++) if (seesGroup(s, c, g)) n = addLink(s, n, (729 + g) * 2);
            }
        } else {
            int g = node - 729;
            int alive = g < s->groupCount;
            int d = alive ? s->groupDigit[g] : 0;
            if (alive) {
                int first = n;
                for (int k = 0; k < 2; k++) {
                    int u = k == 0 ? s->groupLine[g] : 18 + s->groupBox[g];
                    int own = 0;
                    for (int i = 0; i < s->groupSize[g]; i++) own |= 1 << positionIn(s->groupCells[g * 3 + i], u);
                    int target = restNode(s, u, d, s->unitPos[u * 9 + d - 1] & ~own, 1);
                    if (target >= 0 && !linked(s, first, n, target * 2 + 1)) n = addLink(s, n, target * 2 + 1);
                }
            }
            s->linkStart[node * 2 + 1] = n;
            if (!alive) continue;
            for (int p = 0; p < 81; p++) if ((s->lc[p] & sudoku_bit(d)) != 0 && seesGroup(s, p, g)) n = addLink(s, n, (p * 9 + d - 1) * 2);
            for (int h = s->groupFirst[d]; h < s->groupFirst[d + 1]; h++) if (h != g && groupWithin(s, h, g)) n = addLink(s, n, (729 + h) * 2);
        }
    }
    s->linkStart[SUDOKU_STATES] = n;
}

static int nodeDigit(const Sudoku *s, int node) {
    return node < 729 ? node % 9 + 1 : s->groupDigit[node - 729];
}

static int seenWord(const Sudoku *s, int node, int w) {
    return node < 729 ? PEER_SET[(node / 9) * 3 + w] : s->groupSeen[(node - 729) * 3 + w];
}

static int targets(Sudoku *s, int from, int to, int write) {
    int ds = nodeDigit(s, from);
    int dn = nodeDigit(s, to);
    int any = 0;
    if (write) memset(s->elimTry, 0, sizeof(s->elimTry));
    if (ds == dn) {
        for (int w = 0; w < 3; w++) {
            int m = seenWord(s, from, w) & seenWord(s, to, w) & s->digitCells[(ds - 1) * 3 + w];
            if (m == 0) continue;
            if (!write) return 1;
            any = 1;
            for (; m != 0; m &= m - 1) s->elimTry[w * 27 + __builtin_ctz(m)] |= sudoku_bit(ds);
        }
    }
    if (from < 729 && to < 729) {
        int sc = from / 9;
        int nc = to / 9;
        if (sc == nc) {
            int rest = s->lc[sc] & ~sudoku_bit(ds) & ~sudoku_bit(dn);
            if (rest != 0) {
                if (!write) return 1;
                any = 1;
                s->elimTry[sc] |= rest;
            }
        } else if (ds != dn && SEE[sc * 81 + nc]) {
            if ((s->lc[sc] & sudoku_bit(dn)) != 0) {
                if (!write) return 1;
                any = 1;
                s->elimTry[sc] |= sudoku_bit(dn);
            }
            if ((s->lc[nc] & sudoku_bit(ds)) != 0) {
                if (!write) return 1;
                any = 1;
                s->elimTry[nc] |= sudoku_bit(ds);
            }
        }
    } else if (ds != dn && (from < 729 || to < 729)) {
        int single = from < 729 ? from : to;
        int group = from < 729 ? to - 729 : from - 729;
        int c = single / 9;
        int dg = s->groupDigit[group];
        if ((s->lc[c] & sudoku_bit(dg)) != 0 && seesGroup(s, c, group)) {
            if (!write) return 1;
            any = 1;
            s->elimTry[c] |= sudoku_bit(dg);
        }
    }
    return any;
}

static void weakElims(Sudoku *s, int x, int y) {
    int xc = x / 9;
    int yc = y / 9;
    int xd = x % 9;
    int yd = y % 9;
    if (xc == yc) {
        s->elimTry[xc] |= s->lc[xc] & ~(1 << xd) & ~(1 << yd);
        return;
    }
    for (int w = 0; w < 3; w++) {
        for (int m = PEER_SET[xc * 3 + w] & PEER_SET[yc * 3 + w] & s->digitCells[xd * 3 + w]; m != 0; m &= m - 1) {
            s->elimTry[w * 27 + __builtin_ctz(m)] |= 1 << xd;
        }
    }
}

static int loopTargets(Sudoku *s, int from, int end) {
    int n = end >> 1;
    if (n == from) return 0;
    int sc = from / 9;
    int nc = n / 9;
    int sd = from % 9;
    int nd = n % 9;
    if (sc == nc ? sd == nd : sd != nd || !SEE[sc * 81 + nc]) return 0;
    memset(s->elimTry, 0, sizeof(s->elimTry));
    weakElims(s, n, from);
    for (int st = end; s->parent[st] >= 0; st = s->parent[st]) {
        int pa = s->parent[st];
        if ((pa & 1) != 0 && (st & 1) == 0) weakElims(s, pa >> 1, st >> 1);
    }
    for (int c = 0; c < 81; c++) if (s->elimTry[c] != 0) return 1;
    return 0;
}

static int chainFrom(Sudoku *s, int from, int loop, int limit) {
    if (++s->markValue >= 0x7fffffff) {
        memset(s->mark, 0, sizeof(s->mark));
        s->markValue = 1;
    }
    int stamp = s->markValue;
    int origin = from * 2;
    s->mark[origin] = stamp;
    s->depth[origin] = 0;
    s->parent[origin] = -1;
    int head = 0;
    int tail = 0;
    s->queue[tail++] = origin;
    while (head < tail) {
        int st = s->queue[head++];
        int d = s->depth[st] + 1;
        if (d >= limit) break;
        for (int k = s->linkStart[st]; k < s->linkStart[st + 1]; k++) {
            int ch = s->linkTo[k];
            if (s->mark[ch] == stamp) continue;
            s->mark[ch] = stamp;
            s->depth[ch] = d;
            s->parent[ch] = st;
            s->queue[tail++] = ch;
            if ((ch & 1) == 0) continue;
            if (loop ? loopTargets(s, from, ch) : targets(s, from, ch >> 1, 0) && targets(s, from, ch >> 1, 1)) {
                memcpy(s->elimBest, s->elimTry, sizeof(s->elimBest));
                return d;
            }
        }
    }
    return 0;
}

static int chains(Sudoku *s, int id) {
    prepareUnits(s);
    if (id == 31) prepareGroups(s);
    else s->groupCount = 0;
    buildLinks(s, id);
    int loop = id == 29 ? 1 : 0;
    int none = 0x7fffffff;
    int best = none;
    int nodes = 729 + s->groupCount;
    for (int from = 0; from < nodes; from++) {
        if (s->linkStart[from * 2] == s->linkStart[from * 2 + 1]) continue;
        int found = chainFrom(s, from, loop == 1, best - loop);
        if (found != 0) best = found + loop;
    }
    if (best == none) return 0;
    for (int c = 0; c < 81; c++) s->lc[c] &= ~s->elimBest[c];
    s->stepRating = SUDOKU_TECH_BASE[id] + chainBonus(best);
    return 1;
}

static int sueDrop(Sudoku *s, int line, int box, int cs, int ni, int a, int nl, int d, int nb, int lineDigits, int boxDigits) {
    int *keep = s->elimTry;
    memset(keep, 0, sizeof(s->elimTry));
    for (int j = 0; j < ni; j++) if ((cs & (1 << j)) != 0) keep[s->inter[j]] = 1;
    for (int j = 0; j < nl; j++) if ((a & (1 << j)) != 0) keep[s->lineRest[j]] = 2;
    for (int j = 0; j < nb; j++) if ((d & (1 << j)) != 0) keep[s->boxRest[j]] = 3;
    int changed = 0;
    for (int k = 0; k < 9; k++) {
        int c = SUDOKU_UNITS[line][k];
        if (keep[c] != 1 && keep[c] != 2 && drop(s, c, lineDigits)) changed = 1;
    }
    for (int k = 0; k < 9; k++) {
        int c = SUDOKU_UNITS[18 + box][k];
        if (keep[c] != 1 && keep[c] != 3 && drop(s, c, boxDigits)) changed = 1;
    }
    return changed;
}

static int sueDeCoq(Sudoku *s) {
    for (int box = 0; box < 9; box++) {
        for (int t = 0; t < 2; t++) {
            for (int i = 0; i < 3; i++) {
                int line = t == 0 ? (box / 3) * 3 + i : 9 + (box % 3) * 3 + i;
                int ni = 0;
                int nl = 0;
                int nb = 0;
                for (int k = 0; k < 9; k++) {
                    int c = SUDOKU_UNITS[line][k];
                    if (s->lc[c] == 0) continue;
                    if (SUDOKU_BOX[c] == box) s->inter[ni++] = c;
                    else s->lineRest[nl++] = c;
                }
                if (ni < 2) continue;
                for (int k = 0; k < 9; k++) {
                    int c = SUDOKU_UNITS[18 + box][k];
                    if (s->lc[c] != 0 && (t == 0 ? SUDOKU_ROW[c] != line : SUDOKU_COL[c] != line - 9)) s->boxRest[nb++] = c;
                }
                s->lineUnion[0] = 0;
                for (int a = 1; a < (1 << nl); a++) s->lineUnion[a] = s->lineUnion[a & (a - 1)] | s->lc[s->lineRest[__builtin_ctz(a)]];
                s->boxUnion[0] = 0;
                for (int a = 1; a < (1 << nb); a++) s->boxUnion[a] = s->boxUnion[a & (a - 1)] | s->lc[s->boxRest[__builtin_ctz(a)]];
                for (int cs = 3; cs < (1 << ni); cs++) {
                    int size = __builtin_popcount(cs);
                    if (size < 2) continue;
                    int v = 0;
                    for (int j = 0; j < ni; j++) if ((cs & (1 << j)) != 0) v |= s->lc[s->inter[j]];
                    if (__builtin_popcount(v) < size + 2) continue;
                    for (int a = 1; a < (1 << nl); a++) {
                        int va = s->lineUnion[a];
                        if ((va & v) == 0) continue;
                        for (int d = 1; d < (1 << nb); d++) {
                            int vd = s->boxUnion[d];
                            if ((va & vd) != 0 || (vd & v) == 0) continue;
                            if (__builtin_popcount(v | va | vd) != size + __builtin_popcount(a) + __builtin_popcount(d)) continue;
                            if (sueDrop(s, line, box, cs, ni, a, nl, d, nb, va | (v & ~vd), vd | (v & ~va))) return 1;
                        }
                    }
                }
            }
        }
    }
    return 0;
}

static void collectAls(Sudoku *s) {
    int n = 0;
    for (int u = 0; u < 27 && n < SUDOKU_MAX_ALS; u++) {
        const int *cells = SUDOKU_UNITS[u];
        int free = 0;
        for (int k = 0; k < 9; k++) if (s->lc[cells[k]] != 0) free |= 1 << k;
        for (int set = 1; set < 512 && n < SUDOKU_MAX_ALS; set++) {
            if ((set & free) != set) continue;
            int m = 0;
            int rows = 0;
            int cols = 0;
            for (int k = 0; k < 9; k++) {
                if ((set & (1 << k)) == 0) continue;
                int c = cells[k];
                m |= s->lc[c];
                rows |= 1 << SUDOKU_ROW[c];
                cols |= 1 << SUDOKU_COL[c];
            }
            if (__builtin_popcount(m) != __builtin_popcount(set) + 1) continue;
            if (u >= 9 && __builtin_popcount(rows) == 1) continue;
            if (u >= 18 && __builtin_popcount(cols) == 1) continue;
            s->alsDigits[n] = m;
            s->alsCells[n * 3] = 0;
            s->alsCells[n * 3 + 1] = 0;
            s->alsCells[n * 3 + 2] = 0;
            for (int k = 0; k < 9; k++) {
                int c = cells[k];
                if ((set & (1 << k)) != 0) s->alsCells[n * 3 + c / 27] |= 1 << (c % 27);
            }
            for (int d = 0; d < 9; d++) {
                int at = (n * 9 + d) * 3;
                int w0 = 0;
                int w1 = 0;
                int w2 = 0;
                int s0 = -1;
                int s1 = -1;
                int s2 = -1;
                for (int k = 0; k < 9; k++) {
                    int c = cells[k];
                    if ((set & (1 << k)) == 0 || (s->lc[c] & (1 << d)) == 0) continue;
                    if (c < 27) w0 |= 1 << c;
                    else if (c < 54) w1 |= 1 << (c - 27);
                    else w2 |= 1 << (c - 54);
                    s0 &= PEER_SET[c * 3];
                    s1 &= PEER_SET[c * 3 + 1];
                    s2 &= PEER_SET[c * 3 + 2];
                }
                int present = (w0 | w1 | w2) != 0;
                s->alsDigitCells[at] = w0;
                s->alsDigitCells[at + 1] = w1;
                s->alsDigitCells[at + 2] = w2;
                s->alsSeen[at] = present ? s0 : 0;
                s->alsSeen[at + 1] = present ? s1 : 0;
                s->alsSeen[at + 2] = present ? s2 : 0;
            }
            n++;
        }
    }
    s->alsCount = n;
}

static int alsOverlap(const Sudoku *s, int i, int j) {
    return ((s->alsCells[i * 3] & s->alsCells[j * 3]) | (s->alsCells[i * 3 + 1] & s->alsCells[j * 3 + 1]) | (s->alsCells[i * 3 + 2] & s->alsCells[j * 3 + 2])) != 0;
}

static int alsHas(const Sudoku *s, int i, int c) {
    return (s->alsCells[i * 3 + c / 27] & (1 << (c % 27))) != 0;
}

static int restrictedCommon(const Sudoku *s, int i, int j) {
    int rcc = 0;
    for (int rest = s->alsDigits[i] & s->alsDigits[j]; rest != 0; rest &= rest - 1) {
        int d = __builtin_ctz(rest);
        int a = (i * 9 + d) * 3;
        int b = (j * 9 + d) * 3;
        if (((s->alsDigitCells[b] & ~s->alsSeen[a]) | (s->alsDigitCells[b + 1] & ~s->alsSeen[a + 1]) | (s->alsDigitCells[b + 2] & ~s->alsSeen[a + 2])) != 0) continue;
        rcc |= 1 << d;
    }
    return rcc;
}

static void linkAls(Sudoku *s) {
    int n = 0;
    for (int i = 0; i < s->alsCount; i++) {
        s->alsLinkStart[i] = n;
        for (int j = 0; j < s->alsCount; j++) {
            if (i == j || (s->alsDigits[i] & s->alsDigits[j]) == 0 || alsOverlap(s, i, j)) continue;
            int rcc = restrictedCommon(s, i, j);
            if (rcc == 0 || n >= SUDOKU_MAX_ALS_LINKS) continue;
            s->alsLinkTo[n] = j;
            s->alsLinkMask[n] = rcc;
            n++;
        }
    }
    s->alsLinkStart[s->alsCount] = n;
}

static int dropSeen(Sudoku *s, int i, int j, int d) {
    int changed = 0;
    for (int w = 0; w < 3; w++) {
        for (int m = s->alsSeen[(i * 9 + d) * 3 + w] & s->alsSeen[(j * 9 + d) * 3 + w] & s->digitCells[d * 3 + w]; m != 0; m &= m - 1) {
            if (drop(s, w * 27 + __builtin_ctz(m), 1 << d)) changed = 1;
        }
    }
    return changed;
}

static int alsXz(Sudoku *s) {
    prepareUnits(s);
    collectAls(s);
    for (int i = 0; i < s->alsCount; i++) {
        for (int j = i + 1; j < s->alsCount; j++) {
            int common = s->alsDigits[i] & s->alsDigits[j];
            if (__builtin_popcount(common) < 2 || alsOverlap(s, i, j)) continue;
            int rcc = restrictedCommon(s, i, j);
            if (rcc == 0) continue;
            int changed = 0;
            if (__builtin_popcount(rcc) == 1) {
                for (int rest = common & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(s, i, j, __builtin_ctz(rest))) changed = 1;
            } else {
                for (int rest = rcc; rest != 0; rest &= rest - 1) if (dropSeen(s, i, j, __builtin_ctz(rest))) changed = 1;
                for (int rest = s->alsDigits[i] & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(s, i, i, __builtin_ctz(rest))) changed = 1;
                for (int rest = s->alsDigits[j] & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(s, j, j, __builtin_ctz(rest))) changed = 1;
            }
            if (changed) return 1;
        }
    }
    return 0;
}

static int alsXyWing(Sudoku *s) {
    prepareUnits(s);
    collectAls(s);
    linkAls(s);
    for (int c = 0; c < s->alsCount; c++) {
        for (int p = s->alsLinkStart[c]; p < s->alsLinkStart[c + 1]; p++) {
            int a = s->alsLinkTo[p];
            for (int q = p + 1; q < s->alsLinkStart[c + 1]; q++) {
                int b = s->alsLinkTo[q];
                int common = s->alsDigits[a] & s->alsDigits[b];
                if (common == 0 || alsOverlap(s, a, b)) continue;
                for (int xs = s->alsLinkMask[p]; xs != 0; xs &= xs - 1) {
                    int x = xs & -xs;
                    for (int ys = s->alsLinkMask[q] & ~x; ys != 0; ys &= ys - 1) {
                        int y = ys & -ys;
                        int changed = 0;
                        for (int zs = common & ~x & ~y; zs != 0; zs &= zs - 1) if (dropSeen(s, a, b, __builtin_ctz(zs))) changed = 1;
                        if (changed) return 1;
                    }
                }
            }
        }
    }
    return 0;
}

static int blossom(Sudoku *s, int k, int count, int z, int s0, int s1, int s2, int u0, int u1, int u2) {
    if (k == count) {
        int changed = 0;
        for (int m = s0; m != 0; m &= m - 1) if (drop(s, __builtin_ctz(m), 1 << z)) changed = 1;
        for (int m = s1; m != 0; m &= m - 1) if (drop(s, 27 + __builtin_ctz(m), 1 << z)) changed = 1;
        for (int m = s2; m != 0; m &= m - 1) if (drop(s, 54 + __builtin_ctz(m), 1 << z)) changed = 1;
        return changed;
    }
    for (int p = s->petalStart[k]; p < s->petalStart[k + 1]; p++) {
        int i = s->petals[p];
        if ((s->alsDigits[i] & (1 << z)) == 0) continue;
        int c0 = s->alsCells[i * 3];
        int c1 = s->alsCells[i * 3 + 1];
        int c2 = s->alsCells[i * 3 + 2];
        if (((c0 & u0) | (c1 & u1) | (c2 & u2)) != 0) continue;
        int at = (i * 9 + z) * 3;
        int t0 = s0 & s->alsSeen[at];
        int t1 = s1 & s->alsSeen[at + 1];
        int t2 = s2 & s->alsSeen[at + 2];
        if ((t0 | t1 | t2) == 0) continue;
        if (blossom(s, k + 1, count, z, t0, t1, t2, u0 | c0, u1 | c1, u2 | c2)) return 1;
    }
    return 0;
}

static int deathBlossom(Sudoku *s) {
    prepareUnits(s);
    collectAls(s);
    for (int stem = 0; stem < 81; stem++) {
        int sm = s->lc[stem];
        if (__builtin_popcount(sm) < 2) continue;
        int n = 0;
        int k = 0;
        for (int rest = sm; rest != 0; rest &= rest - 1) {
            int d = __builtin_ctz(rest);
            s->petalStart[k++] = n;
            for (int i = 0; i < s->alsCount; i++) {
                if ((s->alsDigits[i] & (1 << d)) == 0 || alsHas(s, i, stem)) continue;
                if ((s->alsSeen[(i * 9 + d) * 3 + stem / 27] & (1 << (stem % 27))) == 0) continue;
                s->petals[n++] = i;
            }
        }
        s->petalStart[k] = n;
        for (int z = 0; z < 9; z++) {
            if ((sm & (1 << z)) != 0) continue;
            if (blossom(s, 0, k, z, s->digitCells[z * 3], s->digitCells[z * 3 + 1], s->digitCells[z * 3 + 2], 0, 0, 0)) return 1;
        }
    }
    return 0;
}

static int alsChainTargets(Sudoku *s, int a, int b, int x, int firsts) {
    int common = s->alsDigits[a] & s->alsDigits[b] & ~(1 << x);
    int any = 0;
    for (int zs = common; zs != 0; zs &= zs - 1) {
        int z = __builtin_ctz(zs);
        if ((firsts & ~(1 << z)) == 0) continue;
        for (int w = 0; w < 3; w++) {
            int m = s->alsSeen[(a * 9 + z) * 3 + w] & s->alsSeen[(b * 9 + z) * 3 + w] & s->digitCells[z * 3 + w];
            if (m == 0) continue;
            if (!any) memset(s->elimTry, 0, sizeof(s->elimTry));
            any = 1;
            for (; m != 0; m &= m - 1) s->elimTry[w * 27 + __builtin_ctz(m)] |= 1 << z;
        }
    }
    return any;
}

static int alsChainFrom(Sudoku *s, int a, int limit) {
    if (++s->alsMarkValue >= 0x7fffffff) {
        memset(s->alsMark, 0, sizeof(s->alsMark));
        s->alsMarkValue = 1;
    }
    int stamp = s->alsMarkValue;
    int head = 0;
    int tail = 0;
    for (int p = s->alsLinkStart[a]; p < s->alsLinkStart[a + 1]; p++) {
        if (2 >= limit) break;
        int b = s->alsLinkTo[p];
        for (int xs = s->alsLinkMask[p]; xs != 0; xs &= xs - 1) {
            int x = __builtin_ctz(xs);
            int st = b * 9 + x;
            if (s->alsMark[st] != stamp) {
                s->alsMark[st] = stamp;
                s->alsFirst[st] = 0;
                s->alsDepth[st] = 2;
                s->alsQueue[tail++] = st;
            }
            s->alsFirst[st] |= 1 << x;
        }
    }
    while (head < tail) {
        int st = s->alsQueue[head++];
        int b = st / 9;
        int x = st % 9;
        int k = s->alsDepth[st];
        if (k >= limit) break;
        if (b != a && alsChainTargets(s, a, b, x, s->alsFirst[st])) {
            memcpy(s->elimBest, s->elimTry, sizeof(s->elimBest));
            return k;
        }
        if (k + 1 >= limit) continue;
        for (int p = s->alsLinkStart[b]; p < s->alsLinkStart[b + 1]; p++) {
            int c = s->alsLinkTo[p];
            if (c == a) continue;
            for (int ys = s->alsLinkMask[p] & ~(1 << x); ys != 0; ys &= ys - 1) {
                int y = __builtin_ctz(ys);
                int next = c * 9 + y;
                if (s->alsMark[next] != stamp) {
                    s->alsMark[next] = stamp;
                    s->alsFirst[next] = 0;
                    s->alsDepth[next] = k + 1;
                    s->alsQueue[tail++] = next;
                }
                if (s->alsDepth[next] == k + 1) s->alsFirst[next] |= s->alsFirst[st];
            }
        }
    }
    return 0;
}

static int alsChain(Sudoku *s) {
    prepareUnits(s);
    collectAls(s);
    linkAls(s);
    int none = 0x7fffffff;
    int best = none;
    for (int a = 0; a < s->alsCount; a++) {
        int found = alsChainFrom(s, a, best);
        if (found != 0) best = found;
    }
    if (best == none) return 0;
    for (int c = 0; c < 81; c++) s->lc[c] &= ~s->elimBest[c];
    s->stepRating = SUDOKU_TECH_BASE[36] + chainBonus(2 * best - 1);
    return 1;
}

static int reachConsistent(Sudoku *s, int node) {
    if (++s->markValue >= 0x7fffffff) {
        memset(s->mark, 0, sizeof(s->mark));
        s->markValue = 1;
    }
    int stamp = s->markValue;
    int origin = node * 2 + 1;
    s->mark[origin] = stamp;
    int head = 0;
    int tail = 0;
    s->queue[tail++] = origin;
    while (head < tail) {
        int st = s->queue[head++];
        for (int k = s->linkStart[st]; k < s->linkStart[st + 1]; k++) {
            int ch = s->linkTo[k];
            if (s->mark[ch] == stamp) continue;
            if (s->mark[ch ^ 1] == stamp) return 0;
            s->mark[ch] = stamp;
            s->queue[tail++] = ch;
        }
    }
    memset(s->offMask, 0, sizeof(s->offMask));
    memset(s->onMask, 0, sizeof(s->onMask));
    for (int k = 0; k < tail; k++) {
        int st = s->queue[k];
        int n = st >> 1;
        int c = n / 9;
        int b = 1 << (n % 9);
        if ((st & 1) != 0) s->onMask[c] |= b;
        else s->offMask[c] |= b;
    }
    for (int c = 0; c < 81; c++) if (s->lc[c] != 0 && (s->lc[c] & ~s->offMask[c]) == 0) return 0;
    for (int u = 0; u < 27; u++) {
        int have = 0;
        int left = 0;
        for (int k = 0; k < 9; k++) {
            int c = SUDOKU_UNITS[u][k];
            have |= s->lc[c];
            left |= s->lc[c] & ~s->offMask[c];
        }
        if ((have & ~left) != 0) return 0;
    }
    return 1;
}

static int nishio(Sudoku *s) {
    prepareUnits(s);
    s->groupCount = 0;
    buildLinks(s, 30);
    memset(s->elimBest, 0, sizeof(s->elimBest));
    int any = 0;
    for (int node = 0; node < 729; node++) {
        int c = node / 9;
        int b = 1 << (node % 9);
        if ((s->lc[c] & b) == 0 || reachConsistent(s, node)) continue;
        s->elimBest[c] |= b;
        any = 1;
    }
    if (!any) return 0;
    for (int c = 0; c < 81; c++) s->lc[c] &= ~s->elimBest[c];
    return 1;
}

static void joinStatic(Sudoku *s) {
    for (int q = 0; q < 81; q++) s->unionMask[q] |= s->onMask[q] != 0 ? s->onMask[q] : s->lc[q] & ~s->offMask[q];
}

static int narrow(Sudoku *s) {
    int changed = 0;
    for (int q = 0; q < 81; q++) {
        if ((s->lc[q] & ~s->unionMask[q]) == 0) continue;
        s->lc[q] &= s->unionMask[q];
        changed = 1;
    }
    return changed;
}

static int cellForcing(Sudoku *s) {
    prepareUnits(s);
    s->groupCount = 0;
    buildLinks(s, 30);
    for (int c = 0; c < 81; c++) {
        int m = s->lc[c];
        if (__builtin_popcount(m) < 2) continue;
        memset(s->unionMask, 0, sizeof(s->unionMask));
        int branches = 0;
        for (int rest = m; rest != 0; rest &= rest - 1) {
            if (!reachConsistent(s, c * 9 + __builtin_ctz(rest))) continue;
            branches++;
            joinStatic(s);
        }
        if (branches != 0 && narrow(s)) return 1;
    }
    return 0;
}

static int unitForcing(Sudoku *s) {
    prepareUnits(s);
    s->groupCount = 0;
    buildLinks(s, 30);
    for (int u = 0; u < 27; u++) {
        for (int d = 1; d <= 9; d++) {
            int m = s->unitPos[u * 9 + d - 1];
            if (__builtin_popcount(m) < 2) continue;
            memset(s->unionMask, 0, sizeof(s->unionMask));
            int branches = 0;
            for (int rest = m; rest != 0; rest &= rest - 1) {
                if (!reachConsistent(s, SUDOKU_UNITS[u][__builtin_ctz(rest)] * 9 + d - 1)) continue;
                branches++;
                joinStatic(s);
            }
            if (branches != 0 && narrow(s)) return 1;
        }
    }
    return 0;
}

static void assign(Sudoku *s, int at, int c, int d) {
    int keep = ~sudoku_bit(d);
    s->sv[at + c] = d;
    s->sc[at + c] = 0;
    for (int k = 0; k < 20; k++) s->sc[at + SUDOKU_PEERS[c][k]] &= keep;
}

static int settle(Sudoku *s, int level) {
    int at = level * 81;
    for (;;) {
        int progress = 0;
        for (int c = 0; c < 81; c++) {
            if (s->sv[at + c] != 0) continue;
            int m = s->sc[at + c];
            if (m == 0) return 0;
            if (__builtin_popcount(m) == 1) {
                assign(s, at, c, sudoku_digit(m));
                progress = 1;
            }
        }
        for (int u = 0; u < 27; u++) {
            const int *cells = SUDOKU_UNITS[u];
            int once = 0;
            int twice = 0;
            int placed = 0;
            for (int k = 0; k < 9; k++) {
                int c = cells[k];
                if (s->sv[at + c] != 0) {
                    int b = sudoku_bit(s->sv[at + c]);
                    if ((placed & b) != 0) return 0;
                    placed |= b;
                    continue;
                }
                int m = s->sc[at + c];
                twice |= once & m;
                once |= m;
            }
            if ((once | placed) != SUDOKU_ALL) return 0;
            for (int single = once & ~twice & ~placed; single != 0; single &= single - 1) {
                int b = single & -single;
                for (int k = 0; k < 9; k++) {
                    int c = cells[k];
                    if (s->sv[at + c] != 0 || (s->sc[at + c] & b) == 0) continue;
                    assign(s, at, c, sudoku_digit(b));
                    progress = 1;
                    break;
                }
            }
        }
        if (!progress) return 1;
    }
}

static int settleNested(Sudoku *s, int level);

static int branch(Sudoku *s, int level, int from, int cell, int d) {
    int at = level * 81;
    if (from < 0) {
        memcpy(s->sv + at, s->lv, sizeof(s->lv));
        memcpy(s->sc + at, s->lc, sizeof(s->lc));
    } else {
        memcpy(s->sv + at, s->sv + from * 81, sizeof(int) * 81);
        memcpy(s->sc + at, s->sc + from * 81, sizeof(int) * 81);
    }
    if ((s->sc[at + cell] & sudoku_bit(d)) == 0) return 0;
    assign(s, at, cell, d);
    return settleNested(s, level);
}

static int settleNested(Sudoku *s, int level) {
    if (!settle(s, level)) return 0;
    if (level == 0) return 1;
    int at = level * 81;
    int changed = 1;
    while (changed) {
        changed = 0;
        for (int q = 0; q < 81; q++) {
            for (int rest = s->sc[at + q]; rest != 0; rest &= rest - 1) {
                int b = rest & -rest;
                if ((s->sc[at + q] & b) == 0 || branch(s, level - 1, level, q, __builtin_ctz(b) + 1)) continue;
                s->sc[at + q] &= ~b;
                changed = 1;
                if (!settle(s, level)) return 0;
            }
        }
    }
    return 1;
}

static void joinDynamic(Sudoku *s) {
    for (int q = 0; q < 81; q++) s->unionMask[q] |= s->sv[q] != 0 ? sudoku_bit(s->sv[q]) : s->sc[q];
}

static int dynamicNet(Sudoku *s) {
    memset(s->elimBest, 0, sizeof(s->elimBest));
    int any = 0;
    for (int c = 0; c < 81; c++) {
        for (int rest = s->lc[c]; rest != 0; rest &= rest - 1) {
            if (branch(s, 0, -1, c, __builtin_ctz(rest) + 1)) continue;
            s->elimBest[c] |= rest & -rest;
            any = 1;
        }
    }
    if (any) {
        for (int c = 0; c < 81; c++) s->lc[c] &= ~s->elimBest[c];
        return 1;
    }
    for (int c = 0; c < 81; c++) {
        if (__builtin_popcount(s->lc[c]) < 2) continue;
        memset(s->unionMask, 0, sizeof(s->unionMask));
        int branches = 0;
        for (int rest = s->lc[c]; rest != 0; rest &= rest - 1) {
            if (!branch(s, 0, -1, c, __builtin_ctz(rest) + 1)) continue;
            branches++;
            joinDynamic(s);
        }
        if (branches != 0 && narrow(s)) return 1;
    }
    for (int u = 0; u < 27; u++) {
        for (int d = 1; d <= 9; d++) {
            int m = unitMask(s, SUDOKU_UNITS[u], sudoku_bit(d));
            if (__builtin_popcount(m) < 2) continue;
            memset(s->unionMask, 0, sizeof(s->unionMask));
            int branches = 0;
            for (int rest = m; rest != 0; rest &= rest - 1) {
                if (!branch(s, 0, -1, SUDOKU_UNITS[u][__builtin_ctz(rest)], d)) continue;
                branches++;
                joinDynamic(s);
            }
            if (branches != 0 && narrow(s)) return 1;
        }
    }
    return 0;
}

static int nestedNet(Sudoku *s) {
    for (int level = 1; level <= SUDOKU_MAX_NEST; level++) {
        for (int c = 0; c < 81; c++) {
            for (int rest = s->lc[c]; rest != 0; rest &= rest - 1) {
                if (branch(s, level, -1, c, __builtin_ctz(rest) + 1)) continue;
                s->lc[c] &= ~(rest & -rest);
                s->stepRating = SUDOKU_TECH_BASE[41] + level - 1;
                return 1;
            }
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
        case 27:
        case 28:
        case 29:
        case 30:
        case 31: return chains(s, id);
        case 32: return sueDeCoq(s);
        case 33: return alsXz(s);
        case 34: return alsXyWing(s);
        case 35: return deathBlossom(s);
        case 36: return alsChain(s);
        case 37: return nishio(s);
        case 38: return cellForcing(s);
        case 39: return unitForcing(s);
        case 40: return dynamicNet(s);
        case 41: return nestedNet(s);
    }
    return 0;
}

int sudoku_step(Sudoku *s) {
    for (int k = 0; k < s->techLimit; k++) {
        int id = SUDOKU_TECH_ORDER[k];
        if (s->techOff[id]) continue;
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
            s->rating = r;
            return;
        }
    }
}
