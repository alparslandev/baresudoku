package com.baresudoku;

import java.util.Arrays;
import java.util.Random;

final class Variant {
    static final int KIND_DIAGONAL = 0;
    static final int KIND_KILLER = 1;
    static final int KIND_MINI = 2;
    static final int LEVELS = 3;
    static final int UNIT_ROW = 0;
    static final int UNIT_COL = 1;
    static final int UNIT_BOX = 2;
    static final int UNIT_NAKED = 3;
    static final int UNIT_DIAGONAL = 4;
    static final int UNIT_REVEAL = 5;
    static final int MAX_CELLS = 81;
    static final int MAX_UNITS = 29;
    static final int MAX_CAGES = 81;
    static final int MAX_PEERS = 32;
    static final int COMBO_SLOTS = 16;
    static final int NODE_LIMIT = 400000;

    static final class Shape {
        final int kind;
        final int n;
        final int boxH;
        final int boxW = 3;
        final int size;
        final int all;
        int unitCount;
        final int[] unitCells = new int[MAX_UNITS * 9];
        final int[] unitType = new int[MAX_UNITS];
        final int[] cageOf = new int[MAX_CELLS];
        int cageCount;
        final int[] cageSize = new int[MAX_CAGES];
        final int[] cageSum = new int[MAX_CAGES];
        final int[] cageCells = new int[MAX_CAGES * 9];
        final int[] peerCount = new int[MAX_CELLS];
        final int[] peerCells = new int[MAX_CELLS * MAX_PEERS];

        Shape(int kind) {
            this.kind = kind;
            n = kind == KIND_MINI ? 6 : 9;
            boxH = kind == KIND_MINI ? 2 : 3;
            size = n * n;
            all = (1 << n) - 1;
            Arrays.fill(cageOf, -1);
            for (int r = 0; r < n; r++) {
                int u = addUnit(UNIT_ROW);
                for (int k = 0; k < n; k++) unitCells[u * 9 + k] = r * n + k;
            }
            for (int c = 0; c < n; c++) {
                int u = addUnit(UNIT_COL);
                for (int k = 0; k < n; k++) unitCells[u * 9 + k] = k * n + c;
            }
            int across = n / boxW;
            for (int b = 0; b < n; b++) {
                int top = b / across * boxH, left = b % across * boxW;
                int u = addUnit(UNIT_BOX);
                for (int k = 0; k < n; k++) unitCells[u * 9 + k] = (top + k / boxW) * n + left + k % boxW;
            }
            if (kind == KIND_DIAGONAL) {
                int down = addUnit(UNIT_DIAGONAL);
                for (int k = 0; k < n; k++) unitCells[down * 9 + k] = k * n + k;
                int up = addUnit(UNIT_DIAGONAL);
                for (int k = 0; k < n; k++) unitCells[up * 9 + k] = k * n + n - 1 - k;
            }
            buildPeers();
        }

        private int addUnit(int type) {
            int u = unitCount++;
            unitType[u] = type;
            return u;
        }

        void setCages(int[] cells, int[] sums, int count) {
            Arrays.fill(cageOf, -1);
            cageCount = 0;
            for (int k = 0; k < count; k++) {
                cageSize[k] = 0;
                cageSum[k] = sums[k];
            }
            for (int c = 0; c < size; c++) {
                int k = cells[c];
                if (k < 0) continue;
                cageOf[c] = k;
                cageCells[k * 9 + cageSize[k]++] = c;
                if (k + 1 > cageCount) cageCount = k + 1;
            }
            buildPeers();
        }

        private boolean sharesGroup(int a, int b) {
            for (int u = 0; u < unitCount; u++) {
                boolean ha = false, hb = false;
                for (int k = 0; k < n; k++) {
                    int c = unitCells[u * 9 + k];
                    if (c == a) ha = true;
                    if (c == b) hb = true;
                }
                if (ha && hb) return true;
            }
            return cageOf[a] >= 0 && cageOf[a] == cageOf[b];
        }

        private void buildPeers() {
            for (int a = 0; a < size; a++) {
                int m = 0;
                for (int b = 0; b < size; b++) if (b != a && sharesGroup(a, b)) peerCells[a * MAX_PEERS + m++] = b;
                peerCount[a] = m;
            }
        }

        int seen(int[] values, int cell) {
            int used = 0;
            for (int k = 0; k < peerCount[cell]; k++) {
                int v = values[peerCells[cell * MAX_PEERS + k]];
                if (v != 0) used |= 1 << (v - 1);
            }
            return all & ~used;
        }

        boolean cageHead(int cell) {
            int k = cageOf[cell];
            return k >= 0 && cageCells[k * 9] == cell;
        }
    }

    final Shape shape;
    int state;
    final int[] values = new int[MAX_CELLS];
    final int[] found = new int[MAX_CELLS];
    final int[] other = new int[MAX_CELLS];
    final int[] solution = new int[MAX_CELLS];
    final int[] full = new int[MAX_CELLS];
    final int[] puzzle = new int[MAX_CELLS];
    final int[] order = new int[MAX_CELLS];
    final int[] cages = new int[MAX_CELLS];
    final int[] sums = new int[MAX_CAGES];
    final int[] members = new int[4];
    final int[] keyGivens = new int[MAX_CELLS];
    final int[] keyCages = new int[MAX_CELLS];
    final int[] keySums = new int[MAX_CAGES];
    int keyCount;
    boolean keyValid;
    int count;
    int limit;
    int nodes;
    int stepCell = -1;
    int stepDigit;
    int stepUnit;
    int hintTech;
    final int[] combos = new int[10 * 46 * COMBO_SLOTS];
    final int[] comboCount = new int[10 * 46];

    Variant(int kind) {
        this(kind, new Random().nextInt());
    }

    Variant(int kind, int seed) {
        shape = new Shape(kind);
        state = seed;
        for (int m = 1; m < 512; m++) {
            int size = 0, sum = 0;
            for (int d = 1; d <= 9; d++) {
                if ((m & (1 << (d - 1))) != 0) {
                    size++;
                    sum += d;
                }
            }
            int at = size * 46 + sum;
            combos[at * COMBO_SLOTS + comboCount[at]++] = m;
        }
    }

    void seed(int s) {
        state = s;
    }

    private long random() {
        state += 0x6D2B79F5;
        int t = state;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return (t ^ (t >>> 14)) & 0xFFFFFFFFL;
    }

    private int nextInt(int bound) {
        return (int) (random() % bound);
    }

    private void shuffle(int[] a, int length) {
        for (int i = length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private int cageAllowed(int[] values, int cell) {
        Shape s = shape;
        int k = s.cageOf[cell];
        if (k < 0) return s.all;
        int used = 0, sum = 0, left = 0;
        for (int i = 0; i < s.cageSize[k]; i++) {
            int v = values[s.cageCells[k * 9 + i]];
            if (v != 0) {
                used |= 1 << (v - 1);
                sum += v;
            } else left++;
        }
        int rest = s.cageSum[k] - sum;
        if (left == 0 || rest < 1 || rest > 45) return 0;
        int at = left * 46 + rest;
        int mask = 0;
        for (int i = 0; i < comboCount[at]; i++) {
            int m = combos[at * COMBO_SLOTS + i];
            if ((m & used) == 0 && (m & ~s.all) == 0) mask |= m;
        }
        return mask;
    }

    int candidatesAt(int[] values, int cell) {
        return shape.seen(values, cell) & cageAllowed(values, cell);
    }

    private void search() {
        Shape s = shape;
        int best = -1, bestMask = 0, bestCount = 10;
        for (int c = 0; c < s.size; c++) {
            if (values[c] != 0) continue;
            int m = candidatesAt(values, c);
            int n = 0;
            for (int d = 0; d < s.n; d++) if ((m & (1 << d)) != 0) n++;
            if (n == 0) return;
            if (n < bestCount) {
                best = c;
                bestMask = m;
                bestCount = n;
                if (n == 1) break;
            }
        }
        nodes++;
        if (best < 0) {
            if (count == 0) for (int c = 0; c < s.size; c++) found[c] = values[c];
            else if (count == 1) for (int c = 0; c < s.size; c++) other[c] = values[c];
            count++;
            return;
        }
        for (int d = 1; d <= s.n && count < limit && nodes < NODE_LIMIT; d++) {
            if ((bestMask & (1 << (d - 1))) == 0) continue;
            values[best] = d;
            search();
            values[best] = 0;
        }
    }

    int countSolutions(int[] puzzle, int limit) {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) values[c] = puzzle[c];
        for (int c = 0; c < s.size; c++) if (puzzle[c] != 0 && (candidatesWithout(c) & (1 << (puzzle[c] - 1))) == 0) return 0;
        count = 0;
        this.limit = limit;
        nodes = 0;
        search();
        return nodes >= NODE_LIMIT ? limit : count;
    }

    private int candidatesWithout(int cell) {
        int v = values[cell];
        values[cell] = 0;
        int m = candidatesAt(values, cell);
        values[cell] = v;
        return m;
    }

    private boolean fill(int cell) {
        Shape s = shape;
        if (cell == s.size) return true;
        int m = s.seen(values, cell);
        int[] order = new int[9];
        for (int i = 0; i < 9; i++) order[i] = i + 1;
        shuffle(order, s.n);
        for (int i = 0; i < s.n; i++) {
            int d = order[i];
            if ((m & (1 << (d - 1))) == 0) continue;
            values[cell] = d;
            if (fill(cell + 1)) return true;
        }
        values[cell] = 0;
        return false;
    }

    private void fullGrid() {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) {
            values[c] = 0;
            cages[c] = -1;
        }
        s.setCages(cages, sums, 0);
        fill(0);
        for (int c = 0; c < s.size; c++) full[c] = values[c];
    }

    private void makeCages() {
        Shape s = shape;
        int n = s.n;
        int[] cageOf = cages;
        for (int c = 0; c < s.size; c++) {
            cageOf[c] = -1;
            order[c] = c;
        }
        shuffle(order, s.size);
        int count = 0;
        for (int i = 0; i < s.size; i++) {
            int start = order[i];
            if (cageOf[start] >= 0) continue;
            int k = count++;
            int want = 2 + nextInt(3);
            int size = 1;
            members[0] = start;
            int used = 1 << (full[start] - 1);
            cageOf[start] = k;
            for (int grow = 0; grow < 12 && size < want; grow++) {
                int from = members[nextInt(size)];
                int way = nextInt(4);
                int dir = way == 0 ? -1 : way == 1 ? 1 : way == 2 ? -n : n;
                int to = from + dir;
                if (to < 0 || to >= s.size) continue;
                if ((dir == -1 || dir == 1) && to / n != from / n) continue;
                if (cageOf[to] >= 0 || (used & (1 << (full[to] - 1))) != 0) continue;
                cageOf[to] = k;
                used |= 1 << (full[to] - 1);
                members[size++] = to;
            }
            int sum = 0;
            for (int m = 0; m < size; m++) sum += full[members[m]];
            sums[k] = sum;
        }
        s.setCages(cageOf, sums, count);
    }

    private boolean singlesSolve(int[] puzzle) {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) values[c] = puzzle[c];
        for (;;) {
            boolean progress = false;
            int empty = 0;
            for (int c = 0; c < s.size; c++) {
                if (values[c] != 0) continue;
                empty++;
                int m = candidatesAt(values, c);
                if (m == 0) return false;
                if ((m & (m - 1)) == 0) {
                    values[c] = Integer.numberOfTrailingZeros(m) + 1;
                    progress = true;
                }
            }
            if (empty == 0) return true;
            for (int u = 0; u < s.unitCount && !progress; u++) {
                for (int d = 1; d <= s.n && !progress; d++) {
                    int where = -1, places = 0;
                    boolean placed = false;
                    for (int k = 0; k < s.n; k++) {
                        int c = s.unitCells[u * 9 + k];
                        if (values[c] == d) placed = true;
                        else if (values[c] == 0 && (candidatesAt(values, c) & (1 << (d - 1))) != 0) {
                            places++;
                            where = c;
                        }
                    }
                    if (!placed && places == 1) {
                        values[where] = d;
                        progress = true;
                    }
                }
            }
            if (!progress) return false;
        }
    }

    private void dig(int level) {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) {
            puzzle[c] = full[c];
            order[c] = c;
        }
        shuffle(order, s.size);
        int keep = level == 0 ? (s.n == 9 ? 36 : 18) : 0;
        int clues = s.size;
        for (int i = 0; i < s.size && clues > keep; i++) {
            int c = order[i], v = puzzle[c];
            puzzle[c] = 0;
            boolean ok = countSolutions(puzzle, 2) == 1 && (level == 2 || singlesSolve(puzzle));
            if (ok) clues--;
            else puzzle[c] = v;
        }
    }

    private void killerGivens(int level) {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) puzzle[c] = 0;
        for (int guard = 0; guard < s.size; guard++) {
            int n = countSolutions(puzzle, 2);
            if (n == 1) break;
            int differ = -1;
            if (n == 2 && nodes < NODE_LIMIT) for (int c = 0; c < s.size && differ < 0; c++) if (puzzle[c] == 0 && found[c] != other[c]) differ = c;
            for (int c = 0; c < s.size && differ < 0; c++) if (puzzle[c] == 0) differ = c;
            puzzle[differ] = full[differ];
        }
        int extra = level == 0 ? 24 : level == 1 ? 10 : 0;
        for (int c = 0; c < s.size; c++) order[c] = c;
        shuffle(order, s.size);
        for (int i = 0, added = 0; i < s.size && added < extra; i++) {
            if (puzzle[order[i]] != 0) continue;
            puzzle[order[i]] = full[order[i]];
            added++;
        }
    }

    int[] generate(int level) {
        Shape s = shape;
        for (;;) {
            fullGrid();
            if (s.kind == KIND_KILLER) {
                makeCages();
                killerGivens(level);
            } else {
                dig(level);
                if (level == 2 && singlesSolve(puzzle)) continue;
            }
            if (countSolutions(puzzle, 2) != 1) continue;
            for (int c = 0; c < s.size; c++) solution[c] = full[c];
            rememberKey(puzzle);
            return Arrays.copyOf(puzzle, s.size);
        }
    }

    private void rememberKey(int[] givens) {
        Shape s = shape;
        for (int c = 0; c < s.size; c++) {
            keyGivens[c] = givens[c];
            keyCages[c] = s.cageOf[c];
        }
        for (int k = 0; k < s.cageCount; k++) keySums[k] = s.cageSum[k];
        keyCount = s.cageCount;
        keyValid = true;
    }

    private boolean sameKey(int[] givens) {
        Shape s = shape;
        if (!keyValid || keyCount != s.cageCount) return false;
        for (int c = 0; c < s.size; c++) if (keyGivens[c] != givens[c] || keyCages[c] != s.cageOf[c]) return false;
        for (int k = 0; k < s.cageCount; k++) if (keySums[k] != s.cageSum[k]) return false;
        return true;
    }

    boolean hint(int[] values, int[] givens) {
        Shape s = shape;
        if (!sameKey(givens)) {
            if (countSolutions(givens, 2) != 1) return false;
            for (int c = 0; c < s.size; c++) solution[c] = found[c];
            rememberKey(givens);
        }
        for (int c = 0; c < s.size; c++) {
            if (values[c] != 0) continue;
            int m = candidatesAt(values, c);
            if (m != 0 && (m & (m - 1)) == 0) {
                int d = Integer.numberOfTrailingZeros(m) + 1;
                if (d != solution[c]) continue;
                stepCell = c;
                stepDigit = d;
                stepUnit = UNIT_NAKED;
                hintTech = 0;
                return true;
            }
        }
        for (int u = 0; u < s.unitCount; u++) {
            for (int d = 1; d <= s.n; d++) {
                int where = -1, places = 0;
                boolean placed = false;
                for (int k = 0; k < s.n; k++) {
                    int c = s.unitCells[u * 9 + k];
                    if (values[c] == d) placed = true;
                    else if (values[c] == 0 && (candidatesAt(values, c) & (1 << (d - 1))) != 0) {
                        places++;
                        where = c;
                    }
                }
                if (placed || places != 1 || solution[where] != d) continue;
                stepCell = where;
                stepDigit = d;
                stepUnit = s.unitType[u];
                hintTech = 0;
                return true;
            }
        }
        int best = -1, bestCount = 10;
        for (int c = 0; c < s.size; c++) {
            if (values[c] != 0) continue;
            int m = candidatesAt(values, c);
            int n = 0;
            for (int d = 0; d < s.n; d++) if ((m & (1 << d)) != 0) n++;
            if (n < bestCount) {
                best = c;
                bestCount = n;
            }
        }
        if (best < 0) return false;
        stepCell = best;
        stepDigit = solution[best];
        stepUnit = UNIT_REVEAL;
        hintTech = 0;
        return true;
    }
}
