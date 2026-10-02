package com.baresudoku;

import java.util.Arrays;

final class SudokuTest {
    static int failures;
    static final String[] LEVEL_NAMES = {"Kolay", "Orta", "Zor", "Uzman", "Usta"};

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        Sudoku engine = new Sudoku(20260929);
        registryTests();
        for (int level = 0; level < Sudoku.LEVELS; level++) {
            long start = System.nanoTime();
            int clues = 0;
            int[] techniques = new int[Sudoku.TECH_COUNT];
            for (int i = 0; i < n; i++) {
                int[] puzzle = engine.generate(level);
                int[] solution = engine.solution;
                check(engine.countSolutions(puzzle, 2) == 1, "tek cozum yok");
                check(Arrays.equals(engine.found, solution), "cozum tam izgarayla eslesmiyor");
                int r = verifiedRate(engine, puzzle, solution);
                check(engine.rate(puzzle) == r, "derece tekrarinda farkli");
                int order = engine.rateOrder;
                boolean levelOk = level < 2 ? r == Sudoku.TECH_BASE[0] : level == 2 ? order >= 1 && order <= 2
                    : level == 3 ? order >= 3 && r < Sudoku.MASTER_RATING : r >= Sudoku.MASTER_RATING;
                check(levelOk, LEVEL_NAMES[level] + " icin derece " + r + " sira " + order);
                techniques[engine.rateTech]++;
                clues += count(puzzle);
                hintWalk(engine, puzzle, solution);
            }
            long ms = (System.nanoTime() - start) / 1000000;
            System.out.printf("%-6s %4d bulmaca, ort ipucu %.1f, %5.1f ms/bulmaca, teknik dagilimi %s%n",
                LEVEL_NAMES[level], n, clues / (double) n, ms / (double) n, Arrays.toString(techniques));
        }
        gameTests(engine);
        stickyTests(engine);
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
            check(e.stepRating >= Sudoku.TECH_BASE[t], "adim derecesi taban altinda");
            if (e.stepRating > max) max = e.stepRating;
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
            check(e.hint(values, puzzle), "ipucu bulunamadi");
            if (values[e.stepCell] != 0) {
                check(false, "ipucu dolu hucreye geldi");
                return;
            }
            check(solution[e.stepCell] == e.stepDigit, "ipucu rakami yanlis");
            check(e.hintTech >= 0 && e.hintTech < Sudoku.TECH_COUNT, "ipucu teknigi aralik disi");
            values[e.stepCell] = e.stepDigit;
            if (++steps > 81) {
                check(false, "ipucu yuruyusu bitmedi");
                return;
            }
        }
    }

    static void registryTests() {
        boolean[] seen = new boolean[Sudoku.TECH_COUNT];
        for (int k = 0; k < Sudoku.TECH_COUNT; k++) {
            int id = Sudoku.TECH_ORDER[k];
            check(!seen[id], "teknik sirasinda tekrar");
            seen[id] = true;
            if (k < 3) check(id == k, "ilk uc teknik yerinde degil");
            if (k > 3) {
                int prev = Sudoku.TECH_ORDER[k - 1];
                check(Sudoku.TECH_BASE[prev] < Sudoku.TECH_BASE[id] || (Sudoku.TECH_BASE[prev] == Sudoku.TECH_BASE[id] && prev < id), "teknik sirasi dereceye gore degil");
            }
            check((Sudoku.TECH_BASE[id] < Sudoku.MASTER_RATING) == (k < Sudoku.EXPERT_LIMIT), "uzman siniri yanlis");
        }
        check(LEVEL_NAMES.length >= Sudoku.LEVELS, "seviye adi eksik");
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

    static void gameTests(Sudoku e) {
        int[] puzzle = e.generate(1);
        Game g = new Game();
        g.start(puzzle, e.solution, 1);
        int cell = firstEmpty(puzzle);
        int right = e.solution[cell];
        int wrong = right == 9 ? 1 : right + 1;
        g.select(cell);
        check(g.enter(wrong), "rakam girilemedi");
        check(g.value[cell] == wrong && g.wrong(cell), "yanlis rakam isaretlenmedi");
        check(g.hint(e) && g.hintKind == Game.HINT_WRONG && g.hintCell == cell, "yanlis rakam ipucusu gelmedi");
        check(g.undo() && g.value[cell] == 0 && g.selected == cell, "geri al degeri dondurmedi");
        g.noteMode = true;
        check(g.enter(right) && g.notes[cell] == Sudoku.bit(right), "not yazilmadi");
        check(g.enter(right) && g.notes[cell] == 0, "not kapanmadi");
        g.noteMode = false;
        check(g.fillNotes(), "notlar dolmadi");
        for (int i = 0; i < 81; i++) {
            if (g.value[i] == 0) check(g.notes[i] == Sudoku.candidates(g.value, i), "not adaylari yanlis");
        }
        check(g.fillNotes(), "dolu notlar temizlenmedi");
        for (int i = 0; i < 81; i++) check(g.notes[i] == 0, "notlar temizlenmedi");
        check(g.fillNotes(), "notlar tekrar dolmadi");
        check(g.hint(e) && g.hintActive() && g.hintKind == Game.HINT_PLACE, "yerlestirme ipucusu gelmedi");
        int hc = g.hintCell;
        int hd = g.hintDigit;
        check(hd == e.solution[hc], "ipucu rakami yanlis");
        check(g.hint(e) && g.value[hc] == hd, "ikinci basis rakami koymadi");
        for (int p : Sudoku.PEERS[hc]) check((g.notes[p] & Sudoku.bit(hd)) == 0, "es hucre notu silinmedi");
        check(g.undo() && g.value[hc] == 0 && g.notes[hc] == Sudoku.candidates(g.value, hc), "ipucu geri alinamadi");
        g.resume(0);
        g.pause(500);
        check(g.time(9999) == 500, "sure yanlis");
        String saved = g.encode(1000);
        Game h = new Game();
        check(h.decode(saved), "kayit okunamadi");
        check(saved.equals(h.encode(0)), "kayit gidip gelince degisti");
        check(h.history.size() == g.history.size() && h.undo(), "kayittan sonra geri al yok");
        check(!new Game().decode("bozuk"), "bozuk kayit kabul edildi");
        check(!new Game().decode(saved.substring(0, 100)), "kesik kayit kabul edildi");
        Game empty = new Game();
        check(empty.decode("0|0|0|0|0|-1|0|||||0") && !empty.active && !empty.showErrors, "bos kayit okunamadi");
        for (int i = 0; i < 81; i++) {
            if (g.value[i] != 0) continue;
            g.select(i);
            check(g.enter(e.solution[i]), "cozum girilemedi");
        }
        check(g.solved && g.selected == Game.NONE, "cozuldu isareti yok");
        check(!g.enter(1) && !g.undo(), "cozulmus oyunda hamle yapildi");
        g.restart();
        check(!g.solved && g.history.isEmpty() && g.selected == Game.NONE && g.elapsed == 0 && Arrays.equals(g.value, g.given), "bastan baslatma sifirlamadi");
        for (int i = 0; i < 81; i++) check(g.notes[i] == 0, "bastan baslatma notlari silmedi");
        g.select(cell);
        check(g.enter(right) && g.value[cell] == right, "bastan baslatilan oyunda hamle yapilamadi");
        System.out.println("Oyun durumu testleri gecti");
    }

    static void stickyTests(Sudoku e) {
        int[] puzzle = e.generate(0);
        Game g = new Game();
        g.start(puzzle, e.solution, 0);
        int a = firstEmpty(puzzle);
        int b = a + 1;
        while (puzzle[b] != 0) b++;
        int d = e.solution[a];
        int other = d == 9 ? 1 : d + 1;
        check(!g.key(d) && g.sticky == d, "secim yokken tus kilitlemedi");
        check(g.tap(a) && g.value[a] == d && g.selected == Game.NONE, "kilitli rakam hucreye yazilmadi veya hucre secili kaldi");
        check(g.tap(a) && g.value[a] == 0 && g.sticky == d, "ayni hucreye ikinci dokunus silmedi");
        check(g.tap(b) && g.value[b] == d, "ikinci hucreye yazilmadi");
        g.select(a);
        check(g.selected == Game.NONE, "kilitliyken surukleme hucre secti");
        check(g.undo() && g.value[b] == 0 && g.sticky == d && g.selected == Game.NONE, "kilitli geri alma kilidi veya secimi bozdu");
        check(g.tap(b) && g.value[b] == d, "geri almadan sonra yazilmadi");
        check(!g.key(d) && g.sticky == 0 && g.value[b] == d, "tusa tekrar basinca kilit acilmadi");
        check(g.undo() && g.value[b] == 0 && g.selected == b, "kilitli yazim geri alinamadi");
        g.select(a);
        check(g.key(d) && g.value[a] == d && g.sticky == 0, "secili hucreye tusla yazilmadi");
        check(!g.key(d) && g.sticky == d && g.value[a] == d && g.selected == Game.NONE, "ayni tusa ikinci basis kilitlemedi veya secimi birakmadi");
        check(!g.key(other) && g.sticky == other && g.value[a] == d, "kilitliyken baska tus degistirmedi");
        check(!g.key(other) && g.sticky == 0, "kilit acilmadi");
        check(!g.tap(a) && g.selected == a, "kilit acilinca dokunus secmedi");
        check(!g.tap(a) && g.selected == Game.NONE, "secili hucreye dokununca secim kalkmadi");
        int given = 0;
        while (puzzle[given] == 0) given++;
        check(!g.tap(given) && g.selected == given, "verilen hucre secilmedi");
        check(!g.key(other) && g.sticky == other && g.selected == Game.NONE, "verilen hucre seciliyken tus kilitlemedi");
        check(!g.tap(given) && g.value[given] == puzzle[given] && g.selected == Game.NONE, "verilen hucre degisti");
        g.noteMode = true;
        check(g.tap(b) && g.notes[b] == Sudoku.bit(other), "kilitli rakam not olarak yazilmadi");
        check(g.tap(b) && g.notes[b] == 0, "kilitli not kapanmadi");
        g.noteMode = false;
        check(!g.key(other) && g.sticky == 0, "kilit acilmadi");
        check(!g.key(d) && g.sticky == d, "tekrar kilitlenmedi");
        check(g.hint(e) && g.sticky == 0 && g.selected == g.hintCell, "ipucu kilidi acmadi");
        g.select(Game.NONE);
        check(!g.key(d) && g.sticky == d, "ipucudan sonra kilitlenmedi");
        for (int i = 0; i < 81; i++) if (g.value[i] == 0 && e.solution[i] == d) check(g.tap(i), "rakam tamamlanirken yazilamadi");
        check(g.remaining(d) == 0 && g.sticky == 0, "rakam tamamlaninca kilit acilmadi");
        check(!g.key(d) && g.sticky == 0, "tamamlanan rakam kilitlendi");
        for (int i = 0; i < 81; i++) {
            if (g.value[i] != 0) continue;
            g.sticky = e.solution[i];
            check(g.tap(i), "cozum kilitli yazilamadi");
        }
        check(g.solved && g.sticky == 0, "cozulunce kilit acilmadi");
        check(!g.tap(a) && !g.key(d) && g.sticky == 0, "cozulmus oyunda kilit calisti");
        System.out.println("Rakam once testleri gecti");
    }

    static int firstEmpty(int[] values) {
        for (int i = 0; i < 81; i++) if (values[i] == 0) return i;
        return -1;
    }
}
