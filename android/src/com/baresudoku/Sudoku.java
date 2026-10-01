package com.baresudoku;

import java.util.Arrays;
import java.util.Random;

final class Sudoku {
    static final int ALL = 0x1FF;
    static final int[] ROW = new int[81];
    static final int[] COL = new int[81];
    static final int[] BOX = new int[81];
    static final int[][] UNITS = new int[27][9];
    static final int[][] PEERS = new int[81][20];
    static final boolean[] SEE = new boolean[6561];

    static {
        for (int i = 0; i < 81; i++) {
            ROW[i] = i / 9;
            COL[i] = i % 9;
            BOX[i] = (i / 27) * 3 + (i % 9) / 3;
        }
        for (int u = 0; u < 9; u++) {
            for (int k = 0; k < 9; k++) {
                UNITS[u][k] = u * 9 + k;
                UNITS[9 + u][k] = k * 9 + u;
                UNITS[18 + u][k] = (u / 3) * 27 + (u % 3) * 3 + (k / 3) * 9 + k % 3;
            }
        }
        for (int i = 0; i < 81; i++) {
            int n = 0;
            for (int j = 0; j < 81; j++) {
                if (sees(i, j)) PEERS[i][n++] = j;
                SEE[i * 81 + j] = sees(i, j);
            }
        }
    }

    static final int[] TECH_BASE = {10, 26, 30, 32, 42, 38, 44, 40, 41, 44, 45, 50, 54, 52, 46, 50, 56, 46, 47, 55, 46, 48, 47, 47, 48, 48, 56, 65, 66, 68, 70, 73, 70, 75, 78, 80, 82, 86, 84, 85, 90, 95};
    static final int TECH_COUNT = TECH_BASE.length;
    static final int MASTER_RATING = 65;
    static final int LEVELS = 5;
    static final int[] TECH_ORDER = new int[TECH_COUNT];
    static final int EXPERT_LIMIT;

    static {
        int n = 0;
        for (int id = 0; id < 11; id++) TECH_ORDER[n++] = id;
        for (int r = 0; r < 128; r++) {
            for (int id = 11; id < TECH_COUNT; id++) if (TECH_BASE[id] == r) TECH_ORDER[n++] = id;
        }
        int expert = 0;
        for (int id = 0; id < TECH_COUNT; id++) if (TECH_BASE[id] < MASTER_RATING) expert++;
        EXPERT_LIMIT = expert;
    }

    final Random random;

    Sudoku() {
        random = new Random();
    }

    Sudoku(long seed) {
        random = new Random(seed);
    }

    static boolean sees(int a, int b) {
        return a != b && (ROW[a] == ROW[b] || COL[a] == COL[b] || BOX[a] == BOX[b]);
    }

    static int bit(int digit) {
        return 1 << (digit - 1);
    }

    static int digit(int singleBit) {
        return Integer.numberOfTrailingZeros(singleBit) + 1;
    }

    static int candidates(int[] values, int cell) {
        int used = 0;
        for (int p : PEERS[cell]) {
            if (values[p] != 0) used |= bit(values[p]);
        }
        return ALL & ~used;
    }

    private final int[] work = new int[81];
    private int count;
    private int limit;
    int[] found;

    int countSolutions(int[] puzzle, int max) {
        System.arraycopy(puzzle, 0, work, 0, 81);
        count = 0;
        limit = max;
        found = null;
        search();
        return count;
    }

    private void search() {
        int best = -1;
        int bestMask = 0;
        int bestSize = 10;
        for (int i = 0; i < 81; i++) {
            if (work[i] != 0) continue;
            int m = candidates(work, i);
            int n = Integer.bitCount(m);
            if (n == 0) return;
            if (n < bestSize) {
                bestSize = n;
                best = i;
                bestMask = m;
                if (n == 1) break;
            }
        }
        if (best < 0) {
            count++;
            if (found == null) found = work.clone();
            return;
        }
        for (int m = bestMask; m != 0 && count < limit; m &= m - 1) {
            work[best] = digit(m & -m);
            search();
        }
        work[best] = 0;
    }

    int[] fullGrid() {
        Arrays.fill(work, 0);
        fillRandom();
        return work.clone();
    }

    private boolean fillRandom() {
        int best = -1;
        int bestMask = 0;
        int bestSize = 10;
        for (int i = 0; i < 81; i++) {
            if (work[i] != 0) continue;
            int m = candidates(work, i);
            int n = Integer.bitCount(m);
            if (n == 0) return false;
            if (n < bestSize) {
                bestSize = n;
                best = i;
                bestMask = m;
                if (n == 1) break;
            }
        }
        if (best < 0) return true;
        int[] digits = new int[bestSize];
        int n = 0;
        for (int m = bestMask; m != 0; m &= m - 1) digits[n++] = digit(m & -m);
        shuffle(digits);
        for (int d : digits) {
            work[best] = d;
            if (fillRandom()) return true;
        }
        work[best] = 0;
        return false;
    }

    private void shuffle(int[] a) {
        for (int k = a.length - 1; k > 0; k--) {
            int j = random.nextInt(k + 1);
            int t = a[k];
            a[k] = a[j];
            a[j] = t;
        }
    }

    int[] dig(int[] full, int minClues) {
        int[] puzzle = full.clone();
        int[] order = new int[41];
        for (int i = 0; i < 41; i++) order[i] = i;
        shuffle(order);
        int clues = 81;
        for (int k = 0; k < 41 && clues > minClues; k++) {
            int a = order[k];
            int b = 80 - a;
            int va = puzzle[a];
            int vb = puzzle[b];
            puzzle[a] = 0;
            puzzle[b] = 0;
            if (keeps(puzzle)) {
                clues -= a == b ? 1 : 2;
            } else {
                puzzle[a] = va;
                puzzle[b] = vb;
            }
        }
        return puzzle;
    }

    private boolean keeps(int[] puzzle) {
        return countSolutions(puzzle, 2) == 1 && (techLimit == TECH_COUNT || rate(puzzle) >= 0);
    }

    private final int[] lv = new int[81];
    private final int[] lc = new int[81];
    int stepCell;
    int stepDigit;
    int stepUnit;

    void load(int[] values) {
        System.arraycopy(values, 0, lv, 0, 81);
        for (int i = 0; i < 81; i++) lc[i] = lv[i] == 0 ? candidates(lv, i) : 0;
    }

    int valueAt(int cell) {
        return lv[cell];
    }

    int candidatesAt(int cell) {
        return lc[cell];
    }

    boolean complete() {
        for (int i = 0; i < 81; i++) if (lv[i] == 0) return false;
        return true;
    }

    boolean stuck() {
        for (int i = 0; i < 81; i++) if (lv[i] == 0 && lc[i] == 0) return true;
        return false;
    }

    private void place(int cell, int d, int unit) {
        lv[cell] = d;
        lc[cell] = 0;
        int keep = ~bit(d);
        for (int p : PEERS[cell]) lc[p] &= keep;
        stepCell = cell;
        stepDigit = d;
        stepUnit = unit;
    }

    boolean singles() {
        for (int i = 0; i < 81; i++) {
            if (lv[i] == 0 && Integer.bitCount(lc[i]) == 1) {
                place(i, digit(lc[i]), 3);
                return true;
            }
        }
        for (int u = 0; u < 27; u++) {
            int[] cells = UNITS[u];
            for (int d = 1; d <= 9; d++) {
                int b = bit(d);
                int where = -1;
                int n = 0;
                for (int k = 0; k < 9; k++) {
                    if ((lc[cells[k]] & b) != 0) {
                        n++;
                        where = cells[k];
                    }
                }
                if (n == 1) {
                    place(where, d, u / 9);
                    return true;
                }
            }
        }
        return false;
    }

    boolean lockedCandidates() {
        boolean changed = false;
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int box = 0; box < 9; box++) {
                int rows = 0;
                int cols = 0;
                for (int k = 0; k < 9; k++) {
                    int c = UNITS[18 + box][k];
                    if ((lc[c] & b) != 0) {
                        rows |= 1 << ROW[c];
                        cols |= 1 << COL[c];
                    }
                }
                if (rows != 0 && Integer.bitCount(rows) == 1) {
                    changed |= clearLineOutsideBox(UNITS[Integer.numberOfTrailingZeros(rows)], box, b);
                }
                if (cols != 0 && Integer.bitCount(cols) == 1) {
                    changed |= clearLineOutsideBox(UNITS[9 + Integer.numberOfTrailingZeros(cols)], box, b);
                }
            }
            for (int line = 0; line < 18; line++) {
                int boxes = 0;
                for (int k = 0; k < 9; k++) {
                    int c = UNITS[line][k];
                    if ((lc[c] & b) != 0) boxes |= 1 << BOX[c];
                }
                if (boxes != 0 && Integer.bitCount(boxes) == 1) {
                    int box = Integer.numberOfTrailingZeros(boxes);
                    for (int k = 0; k < 9; k++) {
                        int c = UNITS[18 + box][k];
                        boolean inLine = line < 9 ? ROW[c] == line : COL[c] == line - 9;
                        if (!inLine && (lc[c] & b) != 0) {
                            lc[c] &= ~b;
                            changed = rated(28);
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean clearLineOutsideBox(int[] line, int box, int b) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            int c = line[k];
            if (BOX[c] != box && (lc[c] & b) != 0) {
                lc[c] &= ~b;
                changed = true;
            }
        }
        return changed;
    }

    private final int[] positions = new int[10];

    boolean subsets() {
        boolean changed = false;
        for (int u = 0; u < 27; u++) {
            int[] cells = UNITS[u];
            for (int a = 0; a < 9; a++) {
                int ma = lc[cells[a]];
                if (ma == 0 || Integer.bitCount(ma) > 3) continue;
                for (int b = a + 1; b < 9; b++) {
                    int mb = lc[cells[b]];
                    if (mb == 0 || Integer.bitCount(mb) > 3) continue;
                    int m2 = ma | mb;
                    if (Integer.bitCount(m2) == 2) {
                        changed |= clearOthers(cells, m2, (1 << a) | (1 << b));
                    } else if (Integer.bitCount(m2) == 3) {
                        for (int c = b + 1; c < 9; c++) {
                            int mc = lc[cells[c]];
                            if (mc != 0 && (mc | m2) == m2) {
                                if (clearOthers(cells, m2, (1 << a) | (1 << b) | (1 << c))) changed = rated(36);
                            }
                        }
                    }
                }
            }
            for (int d = 1; d <= 9; d++) {
                int b = bit(d);
                int m = 0;
                for (int k = 0; k < 9; k++) if ((lc[cells[k]] & b) != 0) m |= 1 << k;
                positions[d] = m;
            }
            for (int d1 = 1; d1 <= 9; d1++) {
                int p1 = positions[d1];
                if (p1 == 0 || Integer.bitCount(p1) > 3) continue;
                for (int d2 = d1 + 1; d2 <= 9; d2++) {
                    int p2 = positions[d2];
                    if (p2 == 0 || Integer.bitCount(p2) > 3) continue;
                    int u2 = p1 | p2;
                    if (Integer.bitCount(u2) == 2) {
                        if (keepOnly(cells, u2, bit(d1) | bit(d2))) changed = rated(34);
                    } else if (Integer.bitCount(u2) == 3) {
                        for (int d3 = d2 + 1; d3 <= 9; d3++) {
                            int p3 = positions[d3];
                            if (p3 != 0 && (p3 | u2) == u2) {
                                if (keepOnly(cells, u2, bit(d1) | bit(d2) | bit(d3))) changed = rated(40);
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean clearOthers(int[] cells, int digits, int members) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            if ((members & (1 << k)) == 0 && (lc[cells[k]] & digits) != 0) {
                lc[cells[k]] &= ~digits;
                changed = true;
            }
        }
        return changed;
    }

    private boolean keepOnly(int[] cells, int members, int digits) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            if ((members & (1 << k)) != 0 && (lc[cells[k]] & ~digits) != 0) {
                lc[cells[k]] &= digits;
                changed = true;
            }
        }
        return changed;
    }

    private final int[] lineMasks = new int[9];

    private int unitMask(int[] cells, int b) {
        int m = 0;
        for (int k = 0; k < 9; k++) if ((lc[cells[k]] & b) != 0) m |= 1 << k;
        return m;
    }

    private void fillLineMasks(int b, int t) {
        for (int line = 0; line < 9; line++) lineMasks[line] = unitMask(UNITS[t * 9 + line], b);
    }

    boolean fish(int size) {
        boolean changed = false;
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int t = 0; t < 2; t++) {
                fillLineMasks(b, t);
                for (int l1 = 0; l1 < 9; l1++) {
                    int m1 = lineMasks[l1];
                    if (m1 == 0 || Integer.bitCount(m1) > size) continue;
                    for (int l2 = l1 + 1; l2 < 9; l2++) {
                        int m2 = lineMasks[l2];
                        if (m2 == 0 || Integer.bitCount(m2) > size) continue;
                        int u2 = m1 | m2;
                        if (size == 2) {
                            if (Integer.bitCount(u2) == 2) changed |= fishClear(b, t, u2, (1 << l1) | (1 << l2));
                        } else if (Integer.bitCount(u2) <= 3) {
                            for (int l3 = l2 + 1; l3 < 9; l3++) {
                                int m3 = lineMasks[l3];
                                if (m3 == 0 || Integer.bitCount(m3) > 3) continue;
                                int u3 = u2 | m3;
                                if (Integer.bitCount(u3) == 3) changed |= fishClear(b, t, u3, (1 << l1) | (1 << l2) | (1 << l3));
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean fishClear(int b, int t, int coverMask, int baseMask) {
        boolean changed = false;
        for (int c = 0; c < 81; c++) {
            int base = t == 0 ? ROW[c] : COL[c];
            int cover = t == 0 ? COL[c] : ROW[c];
            if ((coverMask & (1 << cover)) != 0 && (baseMask & (1 << base)) == 0 && (lc[c] & b) != 0) {
                lc[c] &= ~b;
                changed = true;
            }
        }
        return changed;
    }

    boolean yWing() {
        for (int p = 0; p < 81; p++) {
            int pm = lc[p];
            if (Integer.bitCount(pm) != 2) continue;
            int[] peers = PEERS[p];
            for (int a = 0; a < 20; a++) {
                int am = lc[peers[a]];
                if (Integer.bitCount(am) != 2 || am == pm || Integer.bitCount(am & pm) != 1) continue;
                for (int b = a + 1; b < 20; b++) {
                    int bm = lc[peers[b]];
                    if (Integer.bitCount(bm) != 2 || bm == pm || Integer.bitCount(bm & pm) != 1) continue;
                    if ((am & pm) == (bm & pm)) continue;
                    int z = am & bm & ~pm;
                    if (Integer.bitCount(z) != 1) continue;
                    if (clearSeeing(z, peers[a], peers[b], -1)) return true;
                }
            }
        }
        return false;
    }

    private boolean clearSeeing(int z, int c1, int c2, int c3) {
        boolean changed = false;
        for (int c = 0; c < 81; c++) {
            if (c == c1 || c == c2 || c == c3 || (lc[c] & z) == 0) continue;
            if (!sees(c, c1) || !sees(c, c2) || (c3 >= 0 && !sees(c, c3))) continue;
            lc[c] &= ~z;
            changed = true;
        }
        return changed;
    }

    boolean swordfish() {
        return fish(3);
    }

    boolean xWing() {
        return fish(2);
    }

    boolean xyzWing() {
        for (int p = 0; p < 81; p++) {
            int pm = lc[p];
            if (Integer.bitCount(pm) != 3) continue;
            int[] peers = PEERS[p];
            for (int a = 0; a < 20; a++) {
                int am = lc[peers[a]];
                if (Integer.bitCount(am) != 2 || (am & pm) != am) continue;
                for (int b = a + 1; b < 20; b++) {
                    int bm = lc[peers[b]];
                    if (Integer.bitCount(bm) != 2 || (bm & pm) != bm || bm == am) continue;
                    int z = am & bm;
                    if (Integer.bitCount(z) != 1) continue;
                    if (clearSeeing(z, peers[a], peers[b], p)) return true;
                }
            }
        }
        return false;
    }

    boolean skyscraper() {
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int t = 0; t < 2; t++) {
                fillLineMasks(b, t);
                for (int l1 = 0; l1 < 9; l1++) {
                    int m1 = lineMasks[l1];
                    if (Integer.bitCount(m1) != 2) continue;
                    for (int l2 = l1 + 1; l2 < 9; l2++) {
                        int m2 = lineMasks[l2];
                        if (Integer.bitCount(m2) != 2 || Integer.bitCount(m1 & m2) != 1) continue;
                        int top1 = UNITS[t * 9 + l1][Integer.numberOfTrailingZeros(m1 & ~m2)];
                        int top2 = UNITS[t * 9 + l2][Integer.numberOfTrailingZeros(m2 & ~m1)];
                        if (clearSeeing(b, top1, top2, -1)) return true;
                    }
                }
            }
        }
        return false;
    }

    boolean twoStringKite() {
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int r = 0; r < 9; r++) {
                int rm = unitMask(UNITS[r], b);
                if (Integer.bitCount(rm) != 2) continue;
                int r0 = UNITS[r][Integer.numberOfTrailingZeros(rm)];
                int r1 = UNITS[r][Integer.numberOfTrailingZeros(rm & (rm - 1))];
                for (int c = 0; c < 9; c++) {
                    int cm = unitMask(UNITS[9 + c], b);
                    if (Integer.bitCount(cm) != 2) continue;
                    int c0 = UNITS[9 + c][Integer.numberOfTrailingZeros(cm)];
                    int c1 = UNITS[9 + c][Integer.numberOfTrailingZeros(cm & (cm - 1))];
                    if (r0 == c0 || r0 == c1 || r1 == c0 || r1 == c1) continue;
                    for (int i = 0; i < 2; i++) {
                        int inBox = i == 0 ? r0 : r1;
                        int rowEnd = i == 0 ? r1 : r0;
                        for (int j = 0; j < 2; j++) {
                            int boxMate = j == 0 ? c0 : c1;
                            int colEnd = j == 0 ? c1 : c0;
                            if (BOX[inBox] != BOX[boxMate]) continue;
                            if (clearSeeing(b, rowEnd, colEnd, -1)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    boolean wWing() {
        for (int p = 0; p < 81; p++) {
            int m = lc[p];
            if (Integer.bitCount(m) != 2) continue;
            for (int q = p + 1; q < 81; q++) {
                if (lc[q] != m || sees(p, q)) continue;
                for (int rest = m; rest != 0; rest &= rest - 1) {
                    int b = rest & -rest;
                    for (int u = 0; u < 27; u++) {
                        int um = unitMask(UNITS[u], b);
                        if (Integer.bitCount(um) != 2) continue;
                        int e1 = UNITS[u][Integer.numberOfTrailingZeros(um)];
                        int e2 = UNITS[u][Integer.numberOfTrailingZeros(um & (um - 1))];
                        if (!(sees(e1, p) && sees(e2, q)) && !(sees(e1, q) && sees(e2, p))) continue;
                        if (clearSeeing(m & ~b, p, q, -1)) return true;
                    }
                }
            }
        }
        return false;
    }

    private final int[] corners = new int[4];

    boolean uniqueRectangle() {
        for (int r1 = 0; r1 < 9; r1++) {
            for (int r2 = r1 + 1; r2 < 9; r2++) {
                boolean sameBand = r1 / 3 == r2 / 3;
                for (int c1 = 0; c1 < 9; c1++) {
                    for (int c2 = c1 + 1; c2 < 9; c2++) {
                        if (sameBand == (c1 / 3 == c2 / 3)) continue;
                        corners[0] = r1 * 9 + c1;
                        corners[1] = r1 * 9 + c2;
                        corners[2] = r2 * 9 + c2;
                        corners[3] = r2 * 9 + c1;
                        for (int k = 0; k < 4; k++) {
                            int target = corners[k];
                            int m = lc[corners[(k + 1) & 3]];
                            if (Integer.bitCount(m) != 2 || lc[corners[(k + 2) & 3]] != m || lc[corners[(k + 3) & 3]] != m) continue;
                            if ((lc[target] & m) != m || lc[target] == m) continue;
                            lc[target] &= ~m;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean rated(int r) {
        if (r > stepRating) stepRating = r;
        return true;
    }

    private boolean drop(int cell, int mask) {
        if ((lc[cell] & mask) == 0) return false;
        lc[cell] &= ~mask;
        return true;
    }

    private boolean dropOutside(int[] cells, int digits, int members) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) if ((members & (1 << k)) == 0 && drop(cells[k], digits)) changed = true;
        return changed;
    }

    private boolean keepInside(int[] cells, int members, int digits) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) if ((members & (1 << k)) != 0 && drop(cells[k], ALL & ~digits)) changed = true;
        return changed;
    }

    boolean nakedQuad() {
        for (int u = 0; u < 27; u++) {
            int[] cells = UNITS[u];
            for (int a = 0; a < 9; a++) {
                int ma = lc[cells[a]];
                if (ma == 0 || Integer.bitCount(ma) > 4) continue;
                for (int b = a + 1; b < 9; b++) {
                    int mb = lc[cells[b]];
                    int m2 = ma | mb;
                    if (mb == 0 || Integer.bitCount(m2) > 4) continue;
                    for (int c = b + 1; c < 9; c++) {
                        int mc = lc[cells[c]];
                        int m3 = m2 | mc;
                        if (mc == 0 || Integer.bitCount(m3) > 4) continue;
                        for (int d = c + 1; d < 9; d++) {
                            int md = lc[cells[d]];
                            int m4 = m3 | md;
                            if (md == 0 || Integer.bitCount(m4) != 4) continue;
                            if (dropOutside(cells, m4, (1 << a) | (1 << b) | (1 << c) | (1 << d))) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    boolean hiddenQuad() {
        for (int u = 0; u < 27; u++) {
            int[] cells = UNITS[u];
            for (int d = 1; d <= 9; d++) positions[d] = unitMask(cells, bit(d));
            for (int d1 = 1; d1 <= 9; d1++) {
                int p1 = positions[d1];
                if (p1 == 0 || Integer.bitCount(p1) > 4) continue;
                for (int d2 = d1 + 1; d2 <= 9; d2++) {
                    int p2 = positions[d2];
                    int u2 = p1 | p2;
                    if (p2 == 0 || Integer.bitCount(u2) > 4) continue;
                    for (int d3 = d2 + 1; d3 <= 9; d3++) {
                        int p3 = positions[d3];
                        int u3 = u2 | p3;
                        if (p3 == 0 || Integer.bitCount(u3) > 4) continue;
                        for (int d4 = d3 + 1; d4 <= 9; d4++) {
                            int p4 = positions[d4];
                            int u4 = u3 | p4;
                            if (p4 == 0 || Integer.bitCount(u4) != 4) continue;
                            if (keepInside(cells, u4, bit(d1) | bit(d2) | bit(d3) | bit(d4))) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private int baseUnion(int base) {
        int all = 0;
        for (int l = 0; l < 9; l++) {
            if ((base & (1 << l)) == 0) continue;
            if (lineMasks[l] == 0) return 0;
            all |= lineMasks[l];
        }
        return all;
    }

    private boolean fishDrop(int b, int t, int cover, int base) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            if ((cover & (1 << k)) == 0) continue;
            for (int l = 0; l < 9; l++) if ((base & (1 << l)) == 0 && drop(UNITS[t * 9 + l][k], b)) changed = true;
        }
        return changed;
    }

    boolean jellyfish() {
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int t = 0; t < 2; t++) {
                fillLineMasks(b, t);
                for (int base = 0; base < 512; base++) {
                    if (Integer.bitCount(base) != 4) continue;
                    int cover = baseUnion(base);
                    if (Integer.bitCount(cover) == 4 && fishDrop(b, t, cover, base)) return true;
                }
            }
        }
        return false;
    }

    boolean finnedFish(int n) {
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int t = 0; t < 2; t++) {
                fillLineMasks(b, t);
                for (int base = 0; base < 512; base++) {
                    if (Integer.bitCount(base) != n) continue;
                    int all = baseUnion(base);
                    if (Integer.bitCount(all) <= n) continue;
                    for (int s = 0; s < 3; s++) {
                        int block = 7 << (3 * s);
                        int outside = all & ~block;
                        if (Integer.bitCount(outside) > n) continue;
                        int inside = all & block;
                        for (int sub = inside; ; sub = (sub - 1) & inside) {
                            int cover = outside | sub;
                            if (Integer.bitCount(cover) == n && finnedDrop(b, t, base, cover)) return true;
                            if (sub == 0) break;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean finnedDrop(int b, int t, int base, int cover) {
        int finBox = -1;
        for (int l = 0; l < 9; l++) {
            if ((base & (1 << l)) == 0) continue;
            int fins = lineMasks[l] & ~cover;
            if (fins == 0) continue;
            int box = BOX[UNITS[t * 9 + l][Integer.numberOfTrailingZeros(fins)]];
            if (finBox >= 0 && box != finBox) return false;
            finBox = box;
        }
        if (finBox < 0) return false;
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            if ((cover & (1 << k)) == 0) continue;
            for (int l = 0; l < 9; l++) {
                if ((base & (1 << l)) != 0) continue;
                int c = UNITS[t * 9 + l][k];
                if (BOX[c] == finBox && drop(c, b)) changed = true;
            }
        }
        return changed;
    }

    boolean emptyRectangle() {
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int box = 0; box < 9; box++) {
                int[] cells = UNITS[18 + box];
                int n = 0;
                for (int k = 0; k < 9; k++) if ((lc[cells[k]] & b) != 0) n++;
                if (n < 2) continue;
                int band = box / 3;
                int stack = box % 3;
                for (int i = 0; i < 3; i++) {
                    int row = band * 3 + i;
                    for (int j = 0; j < 3; j++) {
                        int col = stack * 3 + j;
                        boolean cross = true;
                        for (int k = 0; k < 9 && cross; k++) {
                            int c = cells[k];
                            if ((lc[c] & b) != 0 && ROW[c] != row && COL[c] != col) cross = false;
                        }
                        if (!cross) continue;
                        for (int line = 0; line < 9; line++) {
                            if (line / 3 == stack) continue;
                            int m = unitMask(UNITS[9 + line], b);
                            if (Integer.bitCount(m) != 2 || (m & (1 << row)) == 0) continue;
                            int far = Integer.numberOfTrailingZeros(m & ~(1 << row));
                            if (far / 3 != band && drop(far * 9 + col, b)) return true;
                        }
                        for (int line = 0; line < 9; line++) {
                            if (line / 3 == band) continue;
                            int m = unitMask(UNITS[line], b);
                            if (Integer.bitCount(m) != 2 || (m & (1 << col)) == 0) continue;
                            int far = Integer.numberOfTrailingZeros(m & ~(1 << col));
                            if (far / 3 != stack && drop(row * 9 + far, b)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private final int[] dist81 = new int[81];
    private final int[] queue81 = new int[81];
    private final int[] cellList = new int[81];
    private final int[] quad = new int[4];
    private final int[] others = new int[9];

    boolean remotePair() {
        for (int p = 0; p < 81; p++) {
            int m = lc[p];
            if (Integer.bitCount(m) != 2) continue;
            Arrays.fill(dist81, -1);
            dist81[p] = 0;
            int head = 0;
            int tail = 0;
            queue81[tail++] = p;
            while (head < tail) {
                int x = queue81[head++];
                for (int y : PEERS[x]) {
                    if (lc[y] != m || dist81[y] >= 0) continue;
                    dist81[y] = dist81[x] + 1;
                    queue81[tail++] = y;
                }
            }
            for (int k = 1; k < tail; k++) {
                int q = queue81[k];
                if (dist81[q] < 3 || (dist81[q] & 1) == 0) continue;
                boolean changed = false;
                for (int c = 0; c < 81; c++) if (c != p && c != q && SEE[c * 81 + p] && SEE[c * 81 + q] && drop(c, m)) changed = true;
                if (changed) return true;
            }
        }
        return false;
    }

    boolean wxyzWing() {
        int n = 0;
        for (int c = 0; c < 81; c++) if (Integer.bitCount(lc[c]) >= 2 && Integer.bitCount(lc[c]) <= 4) cellList[n++] = c;
        for (int a = 0; a < n; a++) {
            int ma = lc[cellList[a]];
            for (int b = a + 1; b < n; b++) {
                int m2 = ma | lc[cellList[b]];
                if (Integer.bitCount(m2) > 4) continue;
                for (int c = b + 1; c < n; c++) {
                    int m3 = m2 | lc[cellList[c]];
                    if (Integer.bitCount(m3) > 4) continue;
                    for (int d = c + 1; d < n; d++) {
                        int m4 = m3 | lc[cellList[d]];
                        if (Integer.bitCount(m4) != 4) continue;
                        quad[0] = cellList[a];
                        quad[1] = cellList[b];
                        quad[2] = cellList[c];
                        quad[3] = cellList[d];
                        if (wingDrop(m4)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean wingDrop(int union) {
        int z = 0;
        for (int rest = union; rest != 0; rest &= rest - 1) {
            int x = rest & -rest;
            boolean restricted = true;
            for (int i = 0; i < 4 && restricted; i++) {
                if ((lc[quad[i]] & x) == 0) continue;
                for (int j = i + 1; j < 4; j++) if ((lc[quad[j]] & x) != 0 && !SEE[quad[i] * 81 + quad[j]]) restricted = false;
            }
            if (restricted) continue;
            if (z != 0) return false;
            z = x;
        }
        if (z == 0) return false;
        boolean changed = false;
        for (int t = 0; t < 81; t++) {
            if ((lc[t] & z) == 0 || t == quad[0] || t == quad[1] || t == quad[2] || t == quad[3]) continue;
            boolean all = true;
            for (int i = 0; i < 4; i++) if ((lc[quad[i]] & z) != 0 && !SEE[t * 81 + quad[i]]) all = false;
            if (all && drop(t, z)) changed = true;
        }
        return changed;
    }

    private boolean rectangles(int kind) {
        for (int r1 = 0; r1 < 9; r1++) {
            for (int r2 = r1 + 1; r2 < 9; r2++) {
                boolean sameBand = r1 / 3 == r2 / 3;
                for (int c1 = 0; c1 < 9; c1++) {
                    for (int c2 = c1 + 1; c2 < 9; c2++) {
                        if (sameBand == (c1 / 3 == c2 / 3)) continue;
                        corners[0] = r1 * 9 + c1;
                        corners[1] = r1 * 9 + c2;
                        corners[2] = r2 * 9 + c2;
                        corners[3] = r2 * 9 + c1;
                        if (rectangle(kind)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean rectangle(int kind) {
        switch (kind) {
            case 2: return urExtra(false);
            case 3: return urType3();
            case 4: return urType4();
            case 5: return urExtra(true);
            case 6: return urType6();
            default: return hiddenRectangle();
        }
    }

    private boolean dropSeeingCorners(int digits, int members) {
        boolean changed = false;
        for (int t = 0; t < 81; t++) {
            if ((lc[t] & digits) == 0 || t == corners[0] || t == corners[1] || t == corners[2] || t == corners[3]) continue;
            boolean all = true;
            for (int i = 0; i < 4; i++) if ((members & (1 << i)) != 0 && !SEE[t * 81 + corners[i]]) all = false;
            if (all && drop(t, digits)) changed = true;
        }
        return changed;
    }

    private boolean urExtra(boolean diagonal) {
        for (int k = 0; k < 4; k++) {
            int m = lc[corners[k]];
            if (Integer.bitCount(m) != 2) continue;
            int floors = 0;
            int roofs = 0;
            int extra = 0;
            for (int i = 0; i < 4; i++) {
                int v = lc[corners[i]];
                if (v == m) {
                    floors |= 1 << i;
                } else if ((v & m) == m && Integer.bitCount(v) == 3 && (extra == 0 || (v & ~m) == extra)) {
                    extra = v & ~m;
                    roofs |= 1 << i;
                }
            }
            if ((floors | roofs) != 15 || extra == 0 || Integer.bitCount(floors) == 3) continue;
            boolean adjacent = Integer.bitCount(floors) == 2 && floors != 5 && floors != 10;
            if (adjacent != diagonal && dropSeeingCorners(extra, roofs)) return true;
        }
        return false;
    }

    private boolean urType3() {
        for (int k = 0; k < 4; k++) {
            int m = lc[corners[k]];
            if (Integer.bitCount(m) != 2 || lc[corners[(k + 1) & 3]] != m) continue;
            int r1 = corners[(k + 2) & 3];
            int r2 = corners[(k + 3) & 3];
            if ((lc[r1] & m) != m || (lc[r2] & m) != m || lc[r1] == m || lc[r2] == m) continue;
            int extra = (lc[r1] | lc[r2]) & ~m;
            int line = ROW[r1] == ROW[r2] ? ROW[r1] : 9 + COL[r1];
            if (urSubset(UNITS[line], r1, r2, extra)) return true;
            if (BOX[r1] == BOX[r2] && urSubset(UNITS[18 + BOX[r1]], r1, r2, extra)) return true;
        }
        return false;
    }

    private boolean urSubset(int[] cells, int r1, int r2, int extra) {
        int n = 0;
        for (int k = 0; k < 9; k++) {
            int c = cells[k];
            if (c != r1 && c != r2 && lc[c] != 0) others[n++] = c;
        }
        for (int s = 1; s < (1 << n); s++) {
            int size = Integer.bitCount(s);
            if (size > 3) continue;
            int m = extra;
            for (int i = 0; i < n; i++) if ((s & (1 << i)) != 0) m |= lc[others[i]];
            if (Integer.bitCount(m) != size + 1) continue;
            boolean changed = false;
            for (int i = 0; i < n; i++) if ((s & (1 << i)) == 0 && drop(others[i], m)) changed = true;
            if (changed) return true;
        }
        return false;
    }

    private boolean onlyIn(int[] cells, int x, int a, int b) {
        for (int k = 0; k < 9; k++) {
            int c = cells[k];
            if ((lc[c] & x) != 0 && c != a && c != b) return false;
        }
        return true;
    }

    private boolean urType4() {
        for (int k = 0; k < 4; k++) {
            int m = lc[corners[k]];
            if (Integer.bitCount(m) != 2 || lc[corners[(k + 1) & 3]] != m) continue;
            int r1 = corners[(k + 2) & 3];
            int r2 = corners[(k + 3) & 3];
            if ((lc[r1] & m) != m || (lc[r2] & m) != m || (lc[r1] == m && lc[r2] == m)) continue;
            int[] line = ROW[r1] == ROW[r2] ? UNITS[ROW[r1]] : UNITS[9 + COL[r1]];
            int[] box = UNITS[18 + BOX[r1]];
            for (int rest = m; rest != 0; rest &= rest - 1) {
                int x = rest & -rest;
                int y = m & ~x;
                if (!onlyIn(line, x, r1, r2) && !(BOX[r1] == BOX[r2] && onlyIn(box, x, r1, r2))) continue;
                boolean a = drop(r1, y);
                boolean b = drop(r2, y);
                if (a || b) return true;
            }
        }
        return false;
    }

    private boolean urType6() {
        for (int k = 0; k < 2; k++) {
            int m = lc[corners[k]];
            if (Integer.bitCount(m) != 2 || lc[corners[k + 2]] != m) continue;
            int g1 = corners[k + 1];
            int g2 = corners[(k + 3) & 3];
            if ((lc[g1] & m) != m || (lc[g2] & m) != m || lc[g1] == m || lc[g2] == m) continue;
            for (int rest = m; rest != 0; rest &= rest - 1) {
                int x = rest & -rest;
                boolean rows = onlyIn(UNITS[ROW[corners[0]]], x, corners[0], corners[1]) && onlyIn(UNITS[ROW[corners[2]]], x, corners[2], corners[3]);
                boolean cols = onlyIn(UNITS[9 + COL[corners[0]]], x, corners[0], corners[3]) && onlyIn(UNITS[9 + COL[corners[1]]], x, corners[1], corners[2]);
                if (!rows && !cols) continue;
                boolean a = drop(g1, x);
                boolean b = drop(g2, x);
                if (a || b) return true;
            }
        }
        return false;
    }

    private boolean hiddenRectangle() {
        for (int k = 0; k < 4; k++) {
            int a = corners[k];
            int m = lc[a];
            if (Integer.bitCount(m) != 2) continue;
            if ((lc[corners[0]] & m) != m || (lc[corners[1]] & m) != m || (lc[corners[2]] & m) != m || (lc[corners[3]] & m) != m) continue;
            int d = corners[(k + 2) & 3];
            for (int rest = m; rest != 0; rest &= rest - 1) {
                int x = rest & -rest;
                int y = m & ~x;
                if (!onlyIn(UNITS[ROW[d]], x, d, ROW[d] * 9 + COL[a])) continue;
                if (!onlyIn(UNITS[9 + COL[d]], x, d, ROW[a] * 9 + COL[d])) continue;
                if (drop(d, y)) return true;
            }
        }
        return false;
    }

    boolean bugPlusOne() {
        int tri = -1;
        for (int c = 0; c < 81; c++) {
            int n = Integer.bitCount(lc[c]);
            if (n == 0 || n == 2) continue;
            if (n != 3 || tri >= 0) return false;
            tri = c;
        }
        if (tri < 0) return false;
        for (int rest = lc[tri]; rest != 0; rest &= rest - 1) {
            int x = rest & -rest;
            boolean ok = true;
            for (int u = 0; u < 27 && ok; u++) {
                int[] cells = UNITS[u];
                boolean home = u == ROW[tri] || u == 9 + COL[tri] || u == 18 + BOX[tri];
                for (int b = 1; b < 512 && ok; b <<= 1) {
                    int n = home && b == x ? -1 : 0;
                    for (int k = 0; k < 9; k++) if ((lc[cells[k]] & b) != 0) n++;
                    if (n != 0 && n != 2) ok = false;
                }
            }
            if (ok) {
                lc[tri] = x;
                return true;
            }
        }
        return false;
    }

    static final int NODES = 1215;
    static final int STATES = NODES * 2;
    static final int MAX_LINKS = 131072;
    static final int MAX_ALS = 1024;
    static final int MAX_ALS_LINKS = 131072;
    static final int[] PEER_SET = new int[243];

    static {
        for (int c = 0; c < 81; c++) {
            for (int p : PEERS[c]) PEER_SET[c * 3 + p / 27] |= 1 << (p % 27);
        }
    }

    static int positionIn(int c, int u) {
        return u < 9 ? COL[c] : u < 18 ? ROW[c] : (ROW[c] % 3) * 3 + COL[c] % 3;
    }

    static int chainBonus(int links) {
        return links > 4 ? Math.min(10, (links - 4) >> 1) : 0;
    }

    private final int[] unitPos = new int[243];
    private final int[] digitCells = new int[27];
    private int groupCount;
    private final int[] groupFirst = new int[11];
    private final int[] groupDigit = new int[486];
    private final int[] groupCells = new int[1458];
    private final int[] groupSize = new int[486];
    private final int[] groupBox = new int[486];
    private final int[] groupLine = new int[486];
    private final int[] groupSeen = new int[1458];
    private final int[] groupAt = new int[486];
    private final int[] linkStart = new int[STATES + 1];
    private final int[] linkTo = new int[MAX_LINKS];
    private final int[] mark = new int[STATES];
    private int markValue;
    private final int[] depth = new int[STATES];
    private final int[] parent = new int[STATES];
    private final int[] queue = new int[STATES];
    private final int[] elimTry = new int[81];
    private final int[] elimBest = new int[81];
    private final int[] inter = new int[3];
    private final int[] lineRest = new int[6];
    private final int[] boxRest = new int[6];
    private final int[] lineUnion = new int[64];
    private final int[] boxUnion = new int[64];
    private int alsCount;
    private final int[] alsDigits = new int[MAX_ALS];
    private final int[] alsCells = new int[MAX_ALS * 3];
    private final int[] alsDigitCells = new int[MAX_ALS * 27];
    private final int[] alsSeen = new int[MAX_ALS * 27];
    private final int[] alsLinkStart = new int[MAX_ALS + 1];
    private final int[] alsLinkTo = new int[MAX_ALS_LINKS];
    private final int[] alsLinkMask = new int[MAX_ALS_LINKS];
    private final int[] petals = new int[MAX_ALS * 9];
    private final int[] petalStart = new int[10];
    private final int[] alsMark = new int[MAX_ALS * 9];
    private int alsMarkValue;
    private final int[] alsFirst = new int[MAX_ALS * 9];
    private final int[] alsDepth = new int[MAX_ALS * 9];
    private final int[] alsQueue = new int[MAX_ALS * 9];

    private void prepareUnits() {
        for (int u = 0; u < 27; u++) {
            for (int d = 1; d <= 9; d++) unitPos[u * 9 + d - 1] = unitMask(UNITS[u], bit(d));
        }
        Arrays.fill(digitCells, 0);
        for (int c = 0; c < 81; c++) {
            for (int rest = lc[c]; rest != 0; rest &= rest - 1) digitCells[Integer.numberOfTrailingZeros(rest) * 3 + c / 27] |= 1 << (c % 27);
        }
    }

    private void prepareGroups() {
        int n = 0;
        Arrays.fill(groupAt, -1);
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            groupFirst[d] = n;
            for (int box = 0; box < 9; box++) {
                int top = (box / 3) * 3;
                int left = (box % 3) * 3;
                for (int seg = 0; seg < 6; seg++) {
                    int size = 0;
                    for (int k = 0; k < 3; k++) {
                        int c = seg < 3 ? (top + seg) * 9 + left + k : (top + k) * 9 + left + seg - 3;
                        if ((lc[c] & b) != 0) groupCells[n * 3 + size++] = c;
                    }
                    if (size < 2) continue;
                    groupDigit[n] = d;
                    groupSize[n] = size;
                    groupBox[n] = box;
                    groupLine[n] = seg < 3 ? top + seg : 9 + left + seg - 3;
                    for (int w = 0; w < 3; w++) {
                        int seen = -1;
                        for (int k = 0; k < size; k++) seen &= PEER_SET[groupCells[n * 3 + k] * 3 + w];
                        groupSeen[n * 3 + w] = seen;
                    }
                    groupAt[(d - 1) * 54 + box * 6 + seg] = n;
                    n++;
                }
            }
        }
        groupFirst[10] = n;
        groupCount = n;
    }

    private int restNode(int u, int d, int rest, boolean grouped) {
        if (rest == 0) return -1;
        int[] cells = UNITS[u];
        if (Integer.bitCount(rest) == 1) return cells[Integer.numberOfTrailingZeros(rest)] * 9 + d - 1;
        if (!grouped) return -1;
        int first = cells[Integer.numberOfTrailingZeros(rest)];
        boolean sameBox = true;
        boolean sameRow = true;
        boolean sameCol = true;
        for (int m = rest & (rest - 1); m != 0; m &= m - 1) {
            int c = cells[Integer.numberOfTrailingZeros(m)];
            if (BOX[c] != BOX[first]) sameBox = false;
            if (ROW[c] != ROW[first]) sameRow = false;
            if (COL[c] != COL[first]) sameCol = false;
        }
        if (!sameBox || (!sameRow && !sameCol)) return -1;
        int g = groupAt[(d - 1) * 54 + BOX[first] * 6 + (sameRow ? ROW[first] % 3 : 3 + COL[first] % 3)];
        return g >= 0 && groupSize[g] == Integer.bitCount(rest) ? 729 + g : -1;
    }

    private int addLink(int n, int state) {
        if (n >= MAX_LINKS) return n;
        linkTo[n] = state;
        return n + 1;
    }

    private boolean linked(int from, int to, int state) {
        for (int k = from; k < to; k++) if (linkTo[k] == state) return true;
        return false;
    }

    private boolean seesGroup(int c, int g) {
        return (groupSeen[g * 3 + c / 27] & (1 << (c % 27))) != 0;
    }

    private boolean groupWithin(int h, int g) {
        for (int i = 0; i < groupSize[h]; i++) if (!seesGroup(groupCells[h * 3 + i], g)) return false;
        return true;
    }

    private void buildLinks(int id) {
        boolean grouped = id == 31;
        boolean units = id != 28;
        boolean bivalue = id != 27;
        boolean mates = id >= 29;
        int n = 0;
        for (int node = 0; node < NODES; node++) {
            linkStart[node * 2] = n;
            if (node < 729) {
                int c = node / 9;
                int d = node % 9 + 1;
                int b = bit(d);
                boolean alive = (lc[c] & b) != 0;
                if (alive && units) {
                    int first = n;
                    for (int k = 0; k < 3; k++) {
                        int u = k == 0 ? ROW[c] : k == 1 ? 9 + COL[c] : 18 + BOX[c];
                        int target = restNode(u, d, unitPos[u * 9 + d - 1] & ~(1 << positionIn(c, u)), grouped);
                        if (target >= 0 && !linked(first, n, target * 2 + 1)) n = addLink(n, target * 2 + 1);
                    }
                }
                if (alive && bivalue && Integer.bitCount(lc[c]) == 2) n = addLink(n, (c * 9 + Integer.numberOfTrailingZeros(lc[c] & ~b)) * 2 + 1);
                linkStart[node * 2 + 1] = n;
                if (!alive) continue;
                for (int p : PEERS[c]) if ((lc[p] & b) != 0) n = addLink(n, (p * 9 + d - 1) * 2);
                if (mates) {
                    for (int rest = lc[c] & ~b; rest != 0; rest &= rest - 1) n = addLink(n, (c * 9 + Integer.numberOfTrailingZeros(rest)) * 2);
                }
                if (grouped) {
                    for (int g = groupFirst[d]; g < groupFirst[d + 1]; g++) if (seesGroup(c, g)) n = addLink(n, (729 + g) * 2);
                }
            } else {
                int g = node - 729;
                boolean alive = g < groupCount;
                int d = alive ? groupDigit[g] : 0;
                if (alive) {
                    int first = n;
                    for (int k = 0; k < 2; k++) {
                        int u = k == 0 ? groupLine[g] : 18 + groupBox[g];
                        int own = 0;
                        for (int i = 0; i < groupSize[g]; i++) own |= 1 << positionIn(groupCells[g * 3 + i], u);
                        int target = restNode(u, d, unitPos[u * 9 + d - 1] & ~own, true);
                        if (target >= 0 && !linked(first, n, target * 2 + 1)) n = addLink(n, target * 2 + 1);
                    }
                }
                linkStart[node * 2 + 1] = n;
                if (!alive) continue;
                for (int p = 0; p < 81; p++) if ((lc[p] & bit(d)) != 0 && seesGroup(p, g)) n = addLink(n, (p * 9 + d - 1) * 2);
                for (int h = groupFirst[d]; h < groupFirst[d + 1]; h++) if (h != g && groupWithin(h, g)) n = addLink(n, (729 + h) * 2);
            }
        }
        linkStart[STATES] = n;
    }

    private int nodeDigit(int node) {
        return node < 729 ? node % 9 + 1 : groupDigit[node - 729];
    }

    private int seenWord(int node, int w) {
        return node < 729 ? PEER_SET[(node / 9) * 3 + w] : groupSeen[(node - 729) * 3 + w];
    }

    private boolean targets(int s, int n, boolean write) {
        int ds = nodeDigit(s);
        int dn = nodeDigit(n);
        boolean any = false;
        if (write) Arrays.fill(elimTry, 0);
        if (ds == dn) {
            for (int w = 0; w < 3; w++) {
                int m = seenWord(s, w) & seenWord(n, w) & digitCells[(ds - 1) * 3 + w];
                if (m == 0) continue;
                if (!write) return true;
                any = true;
                for (; m != 0; m &= m - 1) elimTry[w * 27 + Integer.numberOfTrailingZeros(m)] |= bit(ds);
            }
        }
        if (s < 729 && n < 729) {
            int sc = s / 9;
            int nc = n / 9;
            if (sc == nc) {
                int rest = lc[sc] & ~bit(ds) & ~bit(dn);
                if (rest != 0) {
                    if (!write) return true;
                    any = true;
                    elimTry[sc] |= rest;
                }
            } else if (ds != dn && SEE[sc * 81 + nc]) {
                if ((lc[sc] & bit(dn)) != 0) {
                    if (!write) return true;
                    any = true;
                    elimTry[sc] |= bit(dn);
                }
                if ((lc[nc] & bit(ds)) != 0) {
                    if (!write) return true;
                    any = true;
                    elimTry[nc] |= bit(ds);
                }
            }
        } else if (ds != dn && (s < 729 || n < 729)) {
            int single = s < 729 ? s : n;
            int group = s < 729 ? n - 729 : s - 729;
            int c = single / 9;
            int dg = groupDigit[group];
            if ((lc[c] & bit(dg)) != 0 && seesGroup(c, group)) {
                if (!write) return true;
                any = true;
                elimTry[c] |= bit(dg);
            }
        }
        return any;
    }

    private void weakElims(int x, int y) {
        int xc = x / 9;
        int yc = y / 9;
        int xd = x % 9;
        int yd = y % 9;
        if (xc == yc) {
            elimTry[xc] |= lc[xc] & ~(1 << xd) & ~(1 << yd);
            return;
        }
        for (int w = 0; w < 3; w++) {
            for (int m = PEER_SET[xc * 3 + w] & PEER_SET[yc * 3 + w] & digitCells[xd * 3 + w]; m != 0; m &= m - 1) {
                elimTry[w * 27 + Integer.numberOfTrailingZeros(m)] |= 1 << xd;
            }
        }
    }

    private boolean loopTargets(int s, int end) {
        int n = end >> 1;
        if (n == s) return false;
        int sc = s / 9;
        int nc = n / 9;
        int sd = s % 9;
        int nd = n % 9;
        if (sc == nc ? sd == nd : sd != nd || !SEE[sc * 81 + nc]) return false;
        Arrays.fill(elimTry, 0);
        weakElims(n, s);
        for (int st = end; parent[st] >= 0; st = parent[st]) {
            int pa = parent[st];
            if ((pa & 1) != 0 && (st & 1) == 0) weakElims(pa >> 1, st >> 1);
        }
        for (int c = 0; c < 81; c++) if (elimTry[c] != 0) return true;
        return false;
    }

    private int chainFrom(int s, boolean loop, int limit) {
        if (++markValue >= 0x7fffffff) {
            Arrays.fill(mark, 0);
            markValue = 1;
        }
        int stamp = markValue;
        int origin = s * 2;
        mark[origin] = stamp;
        depth[origin] = 0;
        parent[origin] = -1;
        int head = 0;
        int tail = 0;
        queue[tail++] = origin;
        while (head < tail) {
            int st = queue[head++];
            int d = depth[st] + 1;
            if (d >= limit) break;
            for (int k = linkStart[st]; k < linkStart[st + 1]; k++) {
                int ch = linkTo[k];
                if (mark[ch] == stamp) continue;
                mark[ch] = stamp;
                depth[ch] = d;
                parent[ch] = st;
                queue[tail++] = ch;
                if ((ch & 1) == 0) continue;
                if (loop ? loopTargets(s, ch) : targets(s, ch >> 1, false) && targets(s, ch >> 1, true)) {
                    System.arraycopy(elimTry, 0, elimBest, 0, 81);
                    return d;
                }
            }
        }
        return 0;
    }

    private boolean chains(int id) {
        prepareUnits();
        if (id == 31) prepareGroups();
        else groupCount = 0;
        buildLinks(id);
        int loop = id == 29 ? 1 : 0;
        int none = 0x7fffffff;
        int best = none;
        int nodes = 729 + groupCount;
        for (int s = 0; s < nodes; s++) {
            if (linkStart[s * 2] == linkStart[s * 2 + 1]) continue;
            int found = chainFrom(s, loop == 1, best - loop);
            if (found != 0) best = found + loop;
        }
        if (best == none) return false;
        for (int c = 0; c < 81; c++) lc[c] &= ~elimBest[c];
        stepRating = TECH_BASE[id] + chainBonus(best);
        return true;
    }

    boolean sueDeCoq() {
        for (int box = 0; box < 9; box++) {
            for (int t = 0; t < 2; t++) {
                for (int i = 0; i < 3; i++) {
                    int line = t == 0 ? (box / 3) * 3 + i : 9 + (box % 3) * 3 + i;
                    int ni = 0;
                    int nl = 0;
                    int nb = 0;
                    for (int k = 0; k < 9; k++) {
                        int c = UNITS[line][k];
                        if (lc[c] == 0) continue;
                        if (BOX[c] == box) inter[ni++] = c;
                        else lineRest[nl++] = c;
                    }
                    if (ni < 2) continue;
                    for (int k = 0; k < 9; k++) {
                        int c = UNITS[18 + box][k];
                        if (lc[c] != 0 && (t == 0 ? ROW[c] != line : COL[c] != line - 9)) boxRest[nb++] = c;
                    }
                    lineUnion[0] = 0;
                    for (int a = 1; a < (1 << nl); a++) lineUnion[a] = lineUnion[a & (a - 1)] | lc[lineRest[Integer.numberOfTrailingZeros(a)]];
                    boxUnion[0] = 0;
                    for (int a = 1; a < (1 << nb); a++) boxUnion[a] = boxUnion[a & (a - 1)] | lc[boxRest[Integer.numberOfTrailingZeros(a)]];
                    for (int cs = 3; cs < (1 << ni); cs++) {
                        int size = Integer.bitCount(cs);
                        if (size < 2) continue;
                        int v = 0;
                        for (int j = 0; j < ni; j++) if ((cs & (1 << j)) != 0) v |= lc[inter[j]];
                        if (Integer.bitCount(v) < size + 2) continue;
                        for (int a = 1; a < (1 << nl); a++) {
                            int va = lineUnion[a];
                            if ((va & v) == 0) continue;
                            for (int d = 1; d < (1 << nb); d++) {
                                int vd = boxUnion[d];
                                if ((va & vd) != 0 || (vd & v) == 0) continue;
                                if (Integer.bitCount(v | va | vd) != size + Integer.bitCount(a) + Integer.bitCount(d)) continue;
                                if (sueDrop(line, box, cs, ni, a, nl, d, nb, va | (v & ~vd), vd | (v & ~va))) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean sueDrop(int line, int box, int cs, int ni, int a, int nl, int d, int nb, int lineDigits, int boxDigits) {
        int[] keep = elimTry;
        Arrays.fill(keep, 0);
        for (int j = 0; j < ni; j++) if ((cs & (1 << j)) != 0) keep[inter[j]] = 1;
        for (int j = 0; j < nl; j++) if ((a & (1 << j)) != 0) keep[lineRest[j]] = 2;
        for (int j = 0; j < nb; j++) if ((d & (1 << j)) != 0) keep[boxRest[j]] = 3;
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            int c = UNITS[line][k];
            if (keep[c] != 1 && keep[c] != 2 && drop(c, lineDigits)) changed = true;
        }
        for (int k = 0; k < 9; k++) {
            int c = UNITS[18 + box][k];
            if (keep[c] != 1 && keep[c] != 3 && drop(c, boxDigits)) changed = true;
        }
        return changed;
    }

    private void collectAls() {
        int n = 0;
        for (int u = 0; u < 27 && n < MAX_ALS; u++) {
            int[] cells = UNITS[u];
            int free = 0;
            for (int k = 0; k < 9; k++) if (lc[cells[k]] != 0) free |= 1 << k;
            for (int s = 1; s < 512 && n < MAX_ALS; s++) {
                if ((s & free) != s) continue;
                int m = 0;
                int rows = 0;
                int cols = 0;
                for (int k = 0; k < 9; k++) {
                    if ((s & (1 << k)) == 0) continue;
                    int c = cells[k];
                    m |= lc[c];
                    rows |= 1 << ROW[c];
                    cols |= 1 << COL[c];
                }
                if (Integer.bitCount(m) != Integer.bitCount(s) + 1) continue;
                if (u >= 9 && Integer.bitCount(rows) == 1) continue;
                if (u >= 18 && Integer.bitCount(cols) == 1) continue;
                alsDigits[n] = m;
                alsCells[n * 3] = 0;
                alsCells[n * 3 + 1] = 0;
                alsCells[n * 3 + 2] = 0;
                for (int k = 0; k < 9; k++) {
                    int c = cells[k];
                    if ((s & (1 << k)) != 0) alsCells[n * 3 + c / 27] |= 1 << (c % 27);
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
                        if ((s & (1 << k)) == 0 || (lc[c] & (1 << d)) == 0) continue;
                        if (c < 27) w0 |= 1 << c;
                        else if (c < 54) w1 |= 1 << (c - 27);
                        else w2 |= 1 << (c - 54);
                        s0 &= PEER_SET[c * 3];
                        s1 &= PEER_SET[c * 3 + 1];
                        s2 &= PEER_SET[c * 3 + 2];
                    }
                    boolean present = (w0 | w1 | w2) != 0;
                    alsDigitCells[at] = w0;
                    alsDigitCells[at + 1] = w1;
                    alsDigitCells[at + 2] = w2;
                    alsSeen[at] = present ? s0 : 0;
                    alsSeen[at + 1] = present ? s1 : 0;
                    alsSeen[at + 2] = present ? s2 : 0;
                }
                n++;
            }
        }
        alsCount = n;
    }

    private boolean alsOverlap(int i, int j) {
        return ((alsCells[i * 3] & alsCells[j * 3]) | (alsCells[i * 3 + 1] & alsCells[j * 3 + 1]) | (alsCells[i * 3 + 2] & alsCells[j * 3 + 2])) != 0;
    }

    private boolean alsHas(int i, int c) {
        return (alsCells[i * 3 + c / 27] & (1 << (c % 27))) != 0;
    }

    private int restrictedCommon(int i, int j) {
        int rcc = 0;
        for (int rest = alsDigits[i] & alsDigits[j]; rest != 0; rest &= rest - 1) {
            int d = Integer.numberOfTrailingZeros(rest);
            int a = (i * 9 + d) * 3;
            int b = (j * 9 + d) * 3;
            if (((alsDigitCells[b] & ~alsSeen[a]) | (alsDigitCells[b + 1] & ~alsSeen[a + 1]) | (alsDigitCells[b + 2] & ~alsSeen[a + 2])) != 0) continue;
            rcc |= 1 << d;
        }
        return rcc;
    }

    private void linkAls() {
        int n = 0;
        for (int i = 0; i < alsCount; i++) {
            alsLinkStart[i] = n;
            for (int j = 0; j < alsCount; j++) {
                if (i == j || (alsDigits[i] & alsDigits[j]) == 0 || alsOverlap(i, j)) continue;
                int rcc = restrictedCommon(i, j);
                if (rcc == 0 || n >= MAX_ALS_LINKS) continue;
                alsLinkTo[n] = j;
                alsLinkMask[n] = rcc;
                n++;
            }
        }
        alsLinkStart[alsCount] = n;
    }

    private boolean dropSeen(int i, int j, int d) {
        boolean changed = false;
        for (int w = 0; w < 3; w++) {
            for (int m = alsSeen[(i * 9 + d) * 3 + w] & alsSeen[(j * 9 + d) * 3 + w] & digitCells[d * 3 + w]; m != 0; m &= m - 1) {
                if (drop(w * 27 + Integer.numberOfTrailingZeros(m), 1 << d)) changed = true;
            }
        }
        return changed;
    }

    boolean alsXz() {
        prepareUnits();
        collectAls();
        for (int i = 0; i < alsCount; i++) {
            for (int j = i + 1; j < alsCount; j++) {
                int common = alsDigits[i] & alsDigits[j];
                if (Integer.bitCount(common) < 2 || alsOverlap(i, j)) continue;
                int rcc = restrictedCommon(i, j);
                if (rcc == 0) continue;
                boolean changed = false;
                if (Integer.bitCount(rcc) == 1) {
                    for (int rest = common & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(i, j, Integer.numberOfTrailingZeros(rest))) changed = true;
                } else {
                    for (int rest = rcc; rest != 0; rest &= rest - 1) if (dropSeen(i, j, Integer.numberOfTrailingZeros(rest))) changed = true;
                    for (int rest = alsDigits[i] & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(i, i, Integer.numberOfTrailingZeros(rest))) changed = true;
                    for (int rest = alsDigits[j] & ~rcc; rest != 0; rest &= rest - 1) if (dropSeen(j, j, Integer.numberOfTrailingZeros(rest))) changed = true;
                }
                if (changed) return true;
            }
        }
        return false;
    }

    boolean alsXyWing() {
        prepareUnits();
        collectAls();
        linkAls();
        for (int c = 0; c < alsCount; c++) {
            for (int p = alsLinkStart[c]; p < alsLinkStart[c + 1]; p++) {
                int a = alsLinkTo[p];
                for (int q = p + 1; q < alsLinkStart[c + 1]; q++) {
                    int b = alsLinkTo[q];
                    int common = alsDigits[a] & alsDigits[b];
                    if (common == 0 || alsOverlap(a, b)) continue;
                    for (int xs = alsLinkMask[p]; xs != 0; xs &= xs - 1) {
                        int x = xs & -xs;
                        for (int ys = alsLinkMask[q] & ~x; ys != 0; ys &= ys - 1) {
                            int y = ys & -ys;
                            boolean changed = false;
                            for (int zs = common & ~x & ~y; zs != 0; zs &= zs - 1) if (dropSeen(a, b, Integer.numberOfTrailingZeros(zs))) changed = true;
                            if (changed) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    boolean deathBlossom() {
        prepareUnits();
        collectAls();
        for (int stem = 0; stem < 81; stem++) {
            int sm = lc[stem];
            if (Integer.bitCount(sm) < 2) continue;
            int n = 0;
            int k = 0;
            for (int rest = sm; rest != 0; rest &= rest - 1) {
                int d = Integer.numberOfTrailingZeros(rest);
                petalStart[k++] = n;
                for (int i = 0; i < alsCount; i++) {
                    if ((alsDigits[i] & (1 << d)) == 0 || alsHas(i, stem)) continue;
                    if ((alsSeen[(i * 9 + d) * 3 + stem / 27] & (1 << (stem % 27))) == 0) continue;
                    petals[n++] = i;
                }
            }
            petalStart[k] = n;
            for (int z = 0; z < 9; z++) {
                if ((sm & (1 << z)) != 0) continue;
                if (blossom(0, k, z, digitCells[z * 3], digitCells[z * 3 + 1], digitCells[z * 3 + 2], 0, 0, 0)) return true;
            }
        }
        return false;
    }

    private boolean blossom(int k, int count, int z, int s0, int s1, int s2, int u0, int u1, int u2) {
        if (k == count) {
            boolean changed = false;
            for (int m = s0; m != 0; m &= m - 1) if (drop(Integer.numberOfTrailingZeros(m), 1 << z)) changed = true;
            for (int m = s1; m != 0; m &= m - 1) if (drop(27 + Integer.numberOfTrailingZeros(m), 1 << z)) changed = true;
            for (int m = s2; m != 0; m &= m - 1) if (drop(54 + Integer.numberOfTrailingZeros(m), 1 << z)) changed = true;
            return changed;
        }
        for (int p = petalStart[k]; p < petalStart[k + 1]; p++) {
            int i = petals[p];
            if ((alsDigits[i] & (1 << z)) == 0) continue;
            int c0 = alsCells[i * 3];
            int c1 = alsCells[i * 3 + 1];
            int c2 = alsCells[i * 3 + 2];
            if (((c0 & u0) | (c1 & u1) | (c2 & u2)) != 0) continue;
            int at = (i * 9 + z) * 3;
            int t0 = s0 & alsSeen[at];
            int t1 = s1 & alsSeen[at + 1];
            int t2 = s2 & alsSeen[at + 2];
            if ((t0 | t1 | t2) == 0) continue;
            if (blossom(k + 1, count, z, t0, t1, t2, u0 | c0, u1 | c1, u2 | c2)) return true;
        }
        return false;
    }

    boolean alsChain() {
        prepareUnits();
        collectAls();
        linkAls();
        int none = 0x7fffffff;
        int best = none;
        for (int a = 0; a < alsCount; a++) {
            int found = alsChainFrom(a, best);
            if (found != 0) best = found;
        }
        if (best == none) return false;
        for (int c = 0; c < 81; c++) lc[c] &= ~elimBest[c];
        stepRating = TECH_BASE[36] + chainBonus(2 * best - 1);
        return true;
    }

    private int alsChainFrom(int a, int limit) {
        if (++alsMarkValue >= 0x7fffffff) {
            Arrays.fill(alsMark, 0);
            alsMarkValue = 1;
        }
        int stamp = alsMarkValue;
        int head = 0;
        int tail = 0;
        for (int p = alsLinkStart[a]; p < alsLinkStart[a + 1]; p++) {
            if (2 >= limit) break;
            int b = alsLinkTo[p];
            for (int xs = alsLinkMask[p]; xs != 0; xs &= xs - 1) {
                int x = Integer.numberOfTrailingZeros(xs);
                int st = b * 9 + x;
                if (alsMark[st] != stamp) {
                    alsMark[st] = stamp;
                    alsFirst[st] = 0;
                    alsDepth[st] = 2;
                    alsQueue[tail++] = st;
                }
                alsFirst[st] |= 1 << x;
            }
        }
        while (head < tail) {
            int st = alsQueue[head++];
            int b = st / 9;
            int x = st % 9;
            int k = alsDepth[st];
            if (k >= limit) break;
            if (b != a && alsChainTargets(a, b, x, alsFirst[st])) {
                System.arraycopy(elimTry, 0, elimBest, 0, 81);
                return k;
            }
            if (k + 1 >= limit) continue;
            for (int p = alsLinkStart[b]; p < alsLinkStart[b + 1]; p++) {
                int c = alsLinkTo[p];
                if (c == a) continue;
                for (int ys = alsLinkMask[p] & ~(1 << x); ys != 0; ys &= ys - 1) {
                    int y = Integer.numberOfTrailingZeros(ys);
                    int next = c * 9 + y;
                    if (alsMark[next] != stamp) {
                        alsMark[next] = stamp;
                        alsFirst[next] = 0;
                        alsDepth[next] = k + 1;
                        alsQueue[tail++] = next;
                    }
                    if (alsDepth[next] == k + 1) alsFirst[next] |= alsFirst[st];
                }
            }
        }
        return 0;
    }

    private boolean alsChainTargets(int a, int b, int x, int firsts) {
        int common = alsDigits[a] & alsDigits[b] & ~(1 << x);
        boolean any = false;
        for (int zs = common; zs != 0; zs &= zs - 1) {
            int z = Integer.numberOfTrailingZeros(zs);
            if ((firsts & ~(1 << z)) == 0) continue;
            for (int w = 0; w < 3; w++) {
                int m = alsSeen[(a * 9 + z) * 3 + w] & alsSeen[(b * 9 + z) * 3 + w] & digitCells[z * 3 + w];
                if (m == 0) continue;
                if (!any) Arrays.fill(elimTry, 0);
                any = true;
                for (; m != 0; m &= m - 1) elimTry[w * 27 + Integer.numberOfTrailingZeros(m)] |= 1 << z;
            }
        }
        return any;
    }

    static final int MAX_NEST = 8;
    private final int[] offMask = new int[81];
    private final int[] onMask = new int[81];
    private final int[] union = new int[81];
    private final int[] sv = new int[81 * (MAX_NEST + 1)];
    private final int[] sc = new int[81 * (MAX_NEST + 1)];

    private boolean reachConsistent(int node) {
        if (++markValue >= 0x7fffffff) {
            Arrays.fill(mark, 0);
            markValue = 1;
        }
        int stamp = markValue;
        int origin = node * 2 + 1;
        mark[origin] = stamp;
        int head = 0;
        int tail = 0;
        queue[tail++] = origin;
        while (head < tail) {
            int st = queue[head++];
            for (int k = linkStart[st]; k < linkStart[st + 1]; k++) {
                int ch = linkTo[k];
                if (mark[ch] == stamp) continue;
                if (mark[ch ^ 1] == stamp) return false;
                mark[ch] = stamp;
                queue[tail++] = ch;
            }
        }
        Arrays.fill(offMask, 0);
        Arrays.fill(onMask, 0);
        for (int k = 0; k < tail; k++) {
            int st = queue[k];
            int n = st >> 1;
            int c = n / 9;
            int b = 1 << (n % 9);
            if ((st & 1) != 0) onMask[c] |= b;
            else offMask[c] |= b;
        }
        for (int c = 0; c < 81; c++) if (lc[c] != 0 && (lc[c] & ~offMask[c]) == 0) return false;
        for (int u = 0; u < 27; u++) {
            int have = 0;
            int left = 0;
            for (int k = 0; k < 9; k++) {
                int c = UNITS[u][k];
                have |= lc[c];
                left |= lc[c] & ~offMask[c];
            }
            if ((have & ~left) != 0) return false;
        }
        return true;
    }

    boolean nishio() {
        prepareUnits();
        groupCount = 0;
        buildLinks(30);
        Arrays.fill(elimBest, 0);
        boolean any = false;
        for (int node = 0; node < 729; node++) {
            int c = node / 9;
            int b = 1 << (node % 9);
            if ((lc[c] & b) == 0 || reachConsistent(node)) continue;
            elimBest[c] |= b;
            any = true;
        }
        if (!any) return false;
        for (int c = 0; c < 81; c++) lc[c] &= ~elimBest[c];
        return true;
    }

    private void joinStatic() {
        for (int q = 0; q < 81; q++) union[q] |= onMask[q] != 0 ? onMask[q] : lc[q] & ~offMask[q];
    }

    private boolean narrow() {
        boolean changed = false;
        for (int q = 0; q < 81; q++) {
            if ((lc[q] & ~union[q]) == 0) continue;
            lc[q] &= union[q];
            changed = true;
        }
        return changed;
    }

    boolean cellForcing() {
        prepareUnits();
        groupCount = 0;
        buildLinks(30);
        for (int c = 0; c < 81; c++) {
            int m = lc[c];
            if (Integer.bitCount(m) < 2) continue;
            Arrays.fill(union, 0);
            int branches = 0;
            for (int rest = m; rest != 0; rest &= rest - 1) {
                if (!reachConsistent(c * 9 + Integer.numberOfTrailingZeros(rest))) continue;
                branches++;
                joinStatic();
            }
            if (branches != 0 && narrow()) return true;
        }
        return false;
    }

    boolean unitForcing() {
        prepareUnits();
        groupCount = 0;
        buildLinks(30);
        for (int u = 0; u < 27; u++) {
            for (int d = 1; d <= 9; d++) {
                int m = unitPos[u * 9 + d - 1];
                if (Integer.bitCount(m) < 2) continue;
                Arrays.fill(union, 0);
                int branches = 0;
                for (int rest = m; rest != 0; rest &= rest - 1) {
                    if (!reachConsistent(UNITS[u][Integer.numberOfTrailingZeros(rest)] * 9 + d - 1)) continue;
                    branches++;
                    joinStatic();
                }
                if (branches != 0 && narrow()) return true;
            }
        }
        return false;
    }

    private void assign(int at, int c, int d) {
        int keep = ~bit(d);
        sv[at + c] = d;
        sc[at + c] = 0;
        for (int p : PEERS[c]) sc[at + p] &= keep;
    }

    private boolean settle(int level) {
        int at = level * 81;
        while (true) {
            boolean progress = false;
            for (int c = 0; c < 81; c++) {
                if (sv[at + c] != 0) continue;
                int m = sc[at + c];
                if (m == 0) return false;
                if (Integer.bitCount(m) == 1) {
                    assign(at, c, digit(m));
                    progress = true;
                }
            }
            for (int u = 0; u < 27; u++) {
                int[] cells = UNITS[u];
                int once = 0;
                int twice = 0;
                int placed = 0;
                for (int k = 0; k < 9; k++) {
                    int c = cells[k];
                    if (sv[at + c] != 0) {
                        int b = bit(sv[at + c]);
                        if ((placed & b) != 0) return false;
                        placed |= b;
                        continue;
                    }
                    int m = sc[at + c];
                    twice |= once & m;
                    once |= m;
                }
                if ((once | placed) != ALL) return false;
                for (int single = once & ~twice & ~placed; single != 0; single &= single - 1) {
                    int b = single & -single;
                    for (int k = 0; k < 9; k++) {
                        int c = cells[k];
                        if (sv[at + c] != 0 || (sc[at + c] & b) == 0) continue;
                        assign(at, c, digit(b));
                        progress = true;
                        break;
                    }
                }
            }
            if (!progress) return true;
        }
    }

    private boolean branch(int level, int from, int cell, int d) {
        int at = level * 81;
        if (from < 0) {
            System.arraycopy(lv, 0, sv, at, 81);
            System.arraycopy(lc, 0, sc, at, 81);
        } else {
            System.arraycopy(sv, from * 81, sv, at, 81);
            System.arraycopy(sc, from * 81, sc, at, 81);
        }
        if ((sc[at + cell] & bit(d)) == 0) return false;
        assign(at, cell, d);
        return settleNested(level);
    }

    private boolean settleNested(int level) {
        if (!settle(level)) return false;
        if (level == 0) return true;
        int at = level * 81;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int q = 0; q < 81; q++) {
                for (int rest = sc[at + q]; rest != 0; rest &= rest - 1) {
                    int b = rest & -rest;
                    if ((sc[at + q] & b) == 0 || branch(level - 1, level, q, Integer.numberOfTrailingZeros(b) + 1)) continue;
                    sc[at + q] &= ~b;
                    changed = true;
                    if (!settle(level)) return false;
                }
            }
        }
        return true;
    }

    private void joinDynamic() {
        for (int q = 0; q < 81; q++) union[q] |= sv[q] != 0 ? bit(sv[q]) : sc[q];
    }

    boolean dynamicNet() {
        Arrays.fill(elimBest, 0);
        boolean any = false;
        for (int c = 0; c < 81; c++) {
            for (int rest = lc[c]; rest != 0; rest &= rest - 1) {
                if (branch(0, -1, c, Integer.numberOfTrailingZeros(rest) + 1)) continue;
                elimBest[c] |= rest & -rest;
                any = true;
            }
        }
        if (any) {
            for (int c = 0; c < 81; c++) lc[c] &= ~elimBest[c];
            return true;
        }
        for (int c = 0; c < 81; c++) {
            if (Integer.bitCount(lc[c]) < 2) continue;
            Arrays.fill(union, 0);
            int branches = 0;
            for (int rest = lc[c]; rest != 0; rest &= rest - 1) {
                if (!branch(0, -1, c, Integer.numberOfTrailingZeros(rest) + 1)) continue;
                branches++;
                joinDynamic();
            }
            if (branches != 0 && narrow()) return true;
        }
        for (int u = 0; u < 27; u++) {
            for (int d = 1; d <= 9; d++) {
                int m = unitMask(UNITS[u], bit(d));
                if (Integer.bitCount(m) < 2) continue;
                Arrays.fill(union, 0);
                int branches = 0;
                for (int rest = m; rest != 0; rest &= rest - 1) {
                    if (!branch(0, -1, UNITS[u][Integer.numberOfTrailingZeros(rest)], d)) continue;
                    branches++;
                    joinDynamic();
                }
                if (branches != 0 && narrow()) return true;
            }
        }
        return false;
    }

    boolean nestedNet() {
        for (int level = 1; level <= MAX_NEST; level++) {
            for (int c = 0; c < 81; c++) {
                for (int rest = lc[c]; rest != 0; rest &= rest - 1) {
                    if (branch(level, -1, c, Integer.numberOfTrailingZeros(rest) + 1)) continue;
                    lc[c] &= ~(rest & -rest);
                    stepRating = TECH_BASE[41] + level - 1;
                    return true;
                }
            }
        }
        return false;
    }

    int techLimit = TECH_COUNT;
    final boolean[] techOff = new boolean[TECH_COUNT];
    int stepRating;
    int stepOrder;
    int rateOrder;
    int rateTech;
    int hintTech;
    int hintRating;
    int[] solution;

    boolean apply(int id) {
        switch (id) {
            case 0: return singles();
            case 1: return lockedCandidates();
            case 2: return subsets();
            case 3: return xWing();
            case 4: return yWing();
            case 5: return swordfish();
            case 6: return xyzWing();
            case 7: return skyscraper();
            case 8: return twoStringKite();
            case 9: return wWing();
            case 10: return uniqueRectangle();
            case 11: return nakedQuad();
            case 12: return hiddenQuad();
            case 13: return jellyfish();
            case 14: return finnedFish(2);
            case 15: return finnedFish(3);
            case 16: return finnedFish(4);
            case 17: return emptyRectangle();
            case 18: return remotePair();
            case 19: return wxyzWing();
            case 20: return rectangles(2);
            case 21: return rectangles(3);
            case 22: return rectangles(4);
            case 23: return rectangles(5);
            case 24: return rectangles(6);
            case 25: return rectangles(7);
            case 26: return bugPlusOne();
            case 27:
            case 28:
            case 29:
            case 30:
            case 31: return chains(id);
            case 32: return sueDeCoq();
            case 33: return alsXz();
            case 34: return alsXyWing();
            case 35: return deathBlossom();
            case 36: return alsChain();
            case 37: return nishio();
            case 38: return cellForcing();
            case 39: return unitForcing();
            case 40: return dynamicNet();
            case 41: return nestedNet();
        }
        return false;
    }

    int step() {
        for (int k = 0; k < techLimit; k++) {
            int id = TECH_ORDER[k];
            if (techOff[id]) continue;
            stepRating = TECH_BASE[id];
            if (apply(id)) {
                stepOrder = k;
                return id;
            }
        }
        return -1;
    }

    int rate(int[] puzzle) {
        load(puzzle);
        int max = 0;
        rateOrder = 0;
        rateTech = 0;
        while (!complete()) {
            if (stuck()) return -1;
            int t = step();
            if (t < 0) return -1;
            if (stepRating > max) {
                max = stepRating;
                rateTech = t;
            }
            if (stepOrder > rateOrder) rateOrder = stepOrder;
        }
        return max;
    }

    private void noteHint(int t) {
        if (stepRating <= hintRating) return;
        hintRating = stepRating;
        hintTech = t;
    }

    boolean hint(int[] values, int[] givens) {
        load(values);
        hintTech = 0;
        hintRating = 0;
        while (!complete() && !stuck()) {
            int t = step();
            if (t < 0) break;
            if (t == 0) return true;
            noteHint(t);
        }
        load(givens);
        hintTech = 0;
        hintRating = 0;
        while (!complete() && !stuck()) {
            int t = step();
            if (t < 0) return false;
            if (t > 0) noteHint(t);
            if (t == 0 && values[stepCell] == 0) return true;
        }
        return false;
    }

    int[] generate(int level) {
        int limit = level < 2 ? 1 : level == 2 ? 3 : level == 3 ? EXPERT_LIMIT : TECH_COUNT;
        int minClues = level == 0 ? 38 : 0;
        while (true) {
            techLimit = limit;
            int[] full = fullGrid();
            int[] puzzle = dig(full, minClues);
            int r = rate(puzzle);
            techLimit = TECH_COUNT;
            boolean ok = r >= 0 && (level < 2 || (level == 2 ? rateOrder >= 1 : level == 3 ? rateOrder >= 3 : r >= MASTER_RATING));
            if (ok) {
                solution = full;
                return puzzle;
            }
        }
    }
}
