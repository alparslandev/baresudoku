#include "Sudoku.h"
#include <string.h>

int SUDOKU_ROW[81];
int SUDOKU_COL[81];
int SUDOKU_BOX[81];
int SUDOKU_UNITS[27][9];
int SUDOKU_PEERS[81][20];
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
        }
    }
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

static int withinAllowed(Sudoku *s, const int *puzzle) {
    int r = sudoku_rate(s, puzzle);
    return r >= 0 && r <= s->allowed;
}

static int keeps(Sudoku *s, const int *puzzle) {
    return sudoku_count_solutions(s, puzzle, 2) == 1 && withinAllowed(s, puzzle);
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
                        changed = 1;
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
                            changed |= clearOthers(s, cells, m2, (1 << a) | (1 << b) | (1 << c));
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
                    changed |= keepOnly(s, cells, u2, sudoku_bit(d1) | sudoku_bit(d2));
                } else if (__builtin_popcount(u2) == 3) {
                    for (int d3 = d2 + 1; d3 <= 9; d3++) {
                        int p3 = s->positions[d3];
                        if (p3 != 0 && (p3 | u2) == u2) {
                            changed |= keepOnly(s, cells, u2, sudoku_bit(d1) | sudoku_bit(d2) | sudoku_bit(d3));
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

int sudoku_step(Sudoku *s) {
    if (singles(s)) return 0;
    if (lockedCandidates(s)) return 1;
    if (subsets(s)) return 2;
    if (fish(s, 2)) return 3;
    if (yWing(s)) return 4;
    if (fish(s, 3)) return 5;
    if (xyzWing(s)) return 6;
    if (skyscraper(s)) return 7;
    if (twoStringKite(s)) return 8;
    if (wWing(s)) return 9;
    if (uniqueRectangle(s)) return 10;
    return -1;
}

int sudoku_rate(Sudoku *s, const int *puzzle) {
    sudoku_load(s, puzzle);
    int max = 0;
    while (!sudoku_complete(s)) {
        if (sudoku_stuck(s)) return -1;
        int t = sudoku_step(s);
        if (t < 0) return -1;
        if (t > max) max = t;
    }
    return max;
}

int sudoku_hint(Sudoku *s, const int *values, const int *givens) {
    sudoku_load(s, values);
    s->hintTech = 0;
    while (!sudoku_complete(s) && !sudoku_stuck(s)) {
        int t = sudoku_step(s);
        if (t < 0) break;
        if (t == 0) return 1;
        if (t > s->hintTech) s->hintTech = t;
    }
    sudoku_load(s, givens);
    s->hintTech = 0;
    while (!sudoku_complete(s) && !sudoku_stuck(s)) {
        int t = sudoku_step(s);
        if (t < 0) return 0;
        if (t > s->hintTech) s->hintTech = t;
        if (t == 0 && values[s->stepCell] == 0) return 1;
    }
    return 0;
}

void sudoku_generate(Sudoku *s, int level, int *puzzle) {
    s->allowed = level < 2 ? 0 : level == 2 ? 2 : 10;
    int minClues = level == 0 ? 38 : 0;
    int full[81];
    for (;;) {
        sudoku_full_grid(s, full);
        sudoku_dig(s, full, minClues, puzzle);
        int r = sudoku_rate(s, puzzle);
        int ok = level == 2 ? r >= 1 : level == 3 ? r >= 3 : r == 0;
        if (ok) {
            memcpy(s->solution, full, sizeof(s->solution));
            return;
        }
    }
}
