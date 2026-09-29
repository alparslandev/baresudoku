package com.baresudoku;

import java.util.Arrays;

final class SudokuTest {
    static int failures;
    static final String[] LEVELS = {"Kolay", "Orta", "Zor", "Uzman"};

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        Sudoku engine = new Sudoku(20260929);
        for (int level = 0; level < 4; level++) {
            long start = System.nanoTime();
            int clues = 0;
            int[] techniques = new int[7];
            for (int i = 0; i < n; i++) {
                int[] puzzle = engine.generate(level);
                int[] solution = engine.solution;
                check(engine.countSolutions(puzzle, 2) == 1, "tek cozum yok");
                check(Arrays.equals(engine.found, solution), "cozum tam izgarayla eslesmiyor");
                int r = verifiedRate(engine, puzzle, solution);
                boolean levelOk = level < 2 ? r == 0 : level == 2 ? r >= 1 && r <= 2 : r >= 3 && r <= 6;
                check(levelOk, LEVELS[level] + " icin derece " + r);
                techniques[r]++;
                clues += count(puzzle);
                hintWalk(engine, puzzle, solution);
            }
            long ms = (System.nanoTime() - start) / 1000000;
            System.out.printf("%-6s %4d bulmaca, ort ipucu %.1f, %5.1f ms/bulmaca, teknik dagilimi %s%n",
                LEVELS[level], n, clues / (double) n, ms / (double) n, Arrays.toString(techniques));
        }
        System.out.println(failures == 0 ? "TAMAM" : "HATA: " + failures);
        if (failures != 0) System.exit(1);
    }

    static int verifiedRate(Sudoku e, int[] puzzle, int[] solution) {
        e.load(puzzle);
        int max = 0;
        while (!e.complete()) {
            check(!e.stuck(), "cikmaz sokak");
            int t = e.step();
            check(t >= 0, "mantikla cozulemedi");
            if (t < 0) return -1;
            if (t > max) max = t;
            for (int i = 0; i < 81; i++) {
                if (e.valueAt(i) != 0) check(e.valueAt(i) == solution[i], "yanlis yerlestirme, teknik " + t);
                else check((e.candidatesAt(i) & Sudoku.bit(solution[i])) != 0, "dogru rakam elendi, teknik " + t);
            }
        }
        return max;
    }

    static void hintWalk(Sudoku e, int[] puzzle, int[] solution) {
        int[] values = puzzle.clone();
        int steps = 0;
        while (count(values) < 81) {
            check(e.hint(values), "ipucu bulunamadi");
            if (values[e.stepCell] != 0) {
                check(false, "ipucu dolu hucreye geldi");
                return;
            }
            check(solution[e.stepCell] == e.stepDigit, "ipucu rakami yanlis");
            check(e.hintTech >= 0 && e.hintTech <= 6, "ipucu teknigi aralik disi");
            values[e.stepCell] = e.stepDigit;
            if (++steps > 81) {
                check(false, "ipucu yuruyusu bitmedi");
                return;
            }
        }
    }

    static int count(int[] values) {
        int n = 0;
        for (int v : values) if (v != 0) n++;
        return n;
    }

    static void check(boolean ok, String what) {
        if (ok) return;
        failures++;
        if (failures <= 20) System.out.println("HATA: " + what);
    }
}
