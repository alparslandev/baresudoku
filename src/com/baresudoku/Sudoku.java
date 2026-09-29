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
            }
        }
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
        return countSolutions(puzzle, 2) == 1 && withinAllowed(puzzle);
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
                            changed = true;
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
                                changed |= clearOthers(cells, m2, (1 << a) | (1 << b) | (1 << c));
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
                        changed |= keepOnly(cells, u2, bit(d1) | bit(d2));
                    } else if (Integer.bitCount(u2) == 3) {
                        for (int d3 = d2 + 1; d3 <= 9; d3++) {
                            int p3 = positions[d3];
                            if (p3 != 0 && (p3 | u2) == u2) {
                                changed |= keepOnly(cells, u2, bit(d1) | bit(d2) | bit(d3));
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

    boolean fish(int size) {
        boolean changed = false;
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int t = 0; t < 2; t++) {
                for (int line = 0; line < 9; line++) {
                    int m = 0;
                    for (int k = 0; k < 9; k++) {
                        if ((lc[UNITS[t * 9 + line][k]] & b) != 0) m |= 1 << k;
                    }
                    lineMasks[line] = m;
                }
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

    int allowed;
    int hintTech;
    int[] solution;

    int step() {
        if (singles()) return 0;
        if (lockedCandidates()) return 1;
        if (subsets()) return 2;
        if (xWing()) return 3;
        if (yWing()) return 4;
        if (swordfish()) return 5;
        if (xyzWing()) return 6;
        return -1;
    }

    int rate(int[] puzzle) {
        load(puzzle);
        int max = 0;
        while (!complete()) {
            if (stuck()) return -1;
            int t = step();
            if (t < 0) return -1;
            if (t > max) max = t;
        }
        return max;
    }

    private boolean withinAllowed(int[] puzzle) {
        int r = rate(puzzle);
        return r >= 0 && r <= allowed;
    }

    boolean hint(int[] values) {
        load(values);
        hintTech = 0;
        while (!complete() && !stuck()) {
            int t = step();
            if (t < 0) return false;
            if (t == 0) return true;
            if (t > hintTech) hintTech = t;
        }
        return false;
    }

    int[] generate(int level) {
        allowed = level < 2 ? 0 : level == 2 ? 2 : 6;
        int minClues = level == 0 ? 38 : 0;
        while (true) {
            int[] full = fullGrid();
            int[] puzzle = dig(full, minClues);
            int r = rate(puzzle);
            boolean ok = level == 2 ? r >= 1 : level == 3 ? r >= 3 : r == 0;
            if (ok) {
                solution = full;
                return puzzle;
            }
        }
    }
}
