#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "../Sudoku.h"
#include "../Game.h"

static int failures;
static const char *LEVELS[] = {"Kolay", "Orta", "Zor", "Uzman"};

static void check(int ok, const char *what) {
    if (ok) return;
    failures++;
    if (failures <= 20) printf("HATA: %s\n", what);
}

static int count(const int *values) {
    int n = 0;
    for (int i = 0; i < 81; i++) if (values[i] != 0) n++;
    return n;
}

static int firstEmpty(const int *values) {
    for (int i = 0; i < 81; i++) if (values[i] == 0) return i;
    return -1;
}

static double nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

static int verifiedRate(Sudoku *e, const int *puzzle, const int *solution) {
    sudoku_load(e, puzzle);
    int max = 0;
    while (!sudoku_complete(e)) {
        check(!sudoku_stuck(e), "cikmaz sokak");
        int t = sudoku_step(e);
        check(t >= 0, "mantikla cozulemedi");
        if (t < 0) return -1;
        if (t > max) max = t;
        for (int i = 0; i < 81; i++) {
            if (e->lv[i] != 0) check(e->lv[i] == solution[i], "yanlis yerlestirme");
            else check((e->lc[i] & sudoku_bit(solution[i])) != 0, "dogru rakam elendi");
        }
    }
    return max;
}

static void hintWalk(Sudoku *e, const int *puzzle, const int *solution) {
    int values[81];
    memcpy(values, puzzle, sizeof(values));
    int steps = 0;
    while (count(values) < 81) {
        check(sudoku_hint(e, values), "ipucu bulunamadi");
        if (values[e->stepCell] != 0) {
            check(0, "ipucu dolu hucreye geldi");
            return;
        }
        check(solution[e->stepCell] == e->stepDigit, "ipucu rakami yanlis");
        values[e->stepCell] = e->stepDigit;
        if (++steps > 81) {
            check(0, "ipucu yuruyusu bitmedi");
            return;
        }
    }
}

static void gameTests(Sudoku *e) {
    int puzzle[81];
    sudoku_generate(e, 1, puzzle);
    Game g;
    game_init(&g);
    game_start(&g, puzzle, e->solution, 1);
    int cell = firstEmpty(puzzle);
    int right = e->solution[cell];
    int wrong = right == 9 ? 1 : right + 1;
    game_select(&g, cell);
    check(game_enter(&g, wrong), "rakam girilemedi");
    check(g.value[cell] == wrong && game_wrong(&g, cell), "yanlis rakam isaretlenmedi");
    check(game_hint(&g, e) && g.hintKind == HINT_WRONG && g.hintCell == cell, "yanlis rakam ipucusu gelmedi");
    check(game_undo(&g) && g.value[cell] == 0 && g.selected == cell, "geri al degeri dondurmedi");
    g.noteMode = 1;
    check(game_enter(&g, right) && g.notes[cell] == sudoku_bit(right), "not yazilmadi");
    check(game_enter(&g, right) && g.notes[cell] == 0, "not kapanmadi");
    g.noteMode = 0;
    check(game_fill_notes(&g), "notlar dolmadi");
    for (int i = 0; i < 81; i++) {
        if (g.value[i] == 0) check(g.notes[i] == sudoku_candidates(g.value, i), "not adaylari yanlis");
    }
    check(game_fill_notes(&g), "dolu notlar temizlenmedi");
    for (int i = 0; i < 81; i++) check(g.notes[i] == 0, "notlar temizlenmedi");
    check(game_fill_notes(&g), "notlar tekrar dolmadi");
    check(game_hint(&g, e) && game_hint_active(&g) && g.hintKind == HINT_PLACE, "yerlestirme ipucusu gelmedi");
    int hc = g.hintCell;
    int hd = g.hintDigit;
    check(hd == e->solution[hc], "ipucu rakami yanlis");
    check(game_hint(&g, e) && g.value[hc] == hd, "ikinci basis rakami koymadi");
    for (int k = 0; k < 20; k++) check((g.notes[SUDOKU_PEERS[hc][k]] & sudoku_bit(hd)) == 0, "es hucre notu silinmedi");
    check(game_undo(&g) && g.value[hc] == 0 && g.notes[hc] == sudoku_candidates(g.value, hc), "ipucu geri alinamadi");
    game_resume(&g, 0);
    game_pause(&g, 500);
    check(game_time(&g, 9999) == 500, "sure yanlis");
    char *saved = game_encode(&g, 1000);
    Game h;
    game_init(&h);
    check(game_decode(&h, saved), "kayit okunamadi");
    char *again = game_encode(&h, 0);
    check(strcmp(saved, again) == 0, "kayit gidip gelince degisti");
    check(h.histCount == g.histCount && game_undo(&h), "kayittan sonra geri al yok");
    Game broken;
    game_init(&broken);
    check(!game_decode(&broken, "bozuk"), "bozuk kayit kabul edildi");
    char cut[101];
    memcpy(cut, saved, 100);
    cut[100] = 0;
    check(!game_decode(&broken, cut), "kesik kayit kabul edildi");
    Game empty;
    game_init(&empty);
    check(game_decode(&empty, "0|0|0|0|0|-1|0|||||0") && !empty.active && !empty.showErrors, "bos kayit okunamadi");
    free(saved);
    free(again);
    for (int i = 0; i < 81; i++) {
        if (g.value[i] != 0) continue;
        game_select(&g, i);
        check(game_enter(&g, e->solution[i]), "cozum girilemedi");
    }
    check(g.solved && g.selected == GAME_NONE, "cozuldu isareti yok");
    check(!game_enter(&g, 1) && !game_undo(&g), "cozulmus oyunda hamle yapildi");
    game_restart(&g);
    check(!g.solved && g.histCount == 0 && g.selected == GAME_NONE && g.elapsed == 0 && memcmp(g.value, g.given, sizeof(g.value)) == 0, "bastan baslatma sifirlamadi");
    for (int i = 0; i < 81; i++) check(g.notes[i] == 0, "bastan baslatma notlari silmedi");
    game_select(&g, cell);
    check(game_enter(&g, right) && g.value[cell] == right, "bastan baslatilan oyunda hamle yapilamadi");
    game_free(&g);
    game_free(&h);
    game_free(&broken);
    game_free(&empty);
    printf("Oyun durumu testleri gecti\n");
}

static void stickyTests(Sudoku *e) {
    int puzzle[81];
    sudoku_generate(e, 0, puzzle);
    Game g;
    game_init(&g);
    game_start(&g, puzzle, e->solution, 0);
    int a = firstEmpty(puzzle);
    int b = a + 1;
    while (puzzle[b] != 0) b++;
    int d = e->solution[a];
    int other = d == 9 ? 1 : d + 1;
    check(!game_key(&g, d) && g.sticky == d, "secim yokken tus kilitlemedi");
    check(game_tap(&g, a) && g.value[a] == d && g.selected == GAME_NONE, "kilitli rakam hucreye yazilmadi veya hucre secili kaldi");
    check(game_tap(&g, a) && g.value[a] == 0 && g.sticky == d, "ayni hucreye ikinci dokunus silmedi");
    check(game_tap(&g, b) && g.value[b] == d, "ikinci hucreye yazilmadi");
    game_select(&g, a);
    check(g.selected == GAME_NONE, "kilitliyken surukleme hucre secti");
    check(game_undo(&g) && g.value[b] == 0 && g.sticky == d && g.selected == GAME_NONE, "kilitli geri alma kilidi veya secimi bozdu");
    check(game_tap(&g, b) && g.value[b] == d, "geri almadan sonra yazilmadi");
    check(!game_key(&g, d) && g.sticky == 0 && g.value[b] == d, "tusa tekrar basinca kilit acilmadi");
    check(game_undo(&g) && g.value[b] == 0 && g.selected == b, "kilitli yazim geri alinamadi");
    game_select(&g, a);
    check(game_key(&g, d) && g.value[a] == d && g.sticky == 0, "secili hucreye tusla yazilmadi");
    check(!game_key(&g, d) && g.sticky == d && g.value[a] == d && g.selected == GAME_NONE, "ayni tusa ikinci basis kilitlemedi veya secimi birakmadi");
    check(!game_key(&g, other) && g.sticky == other && g.value[a] == d, "kilitliyken baska tus degistirmedi");
    check(!game_key(&g, other) && g.sticky == 0, "kilit acilmadi");
    check(!game_tap(&g, a) && g.selected == a, "kilit acilinca dokunus secmedi");
    check(!game_tap(&g, a) && g.selected == GAME_NONE, "secili hucreye dokununca secim kalkmadi");
    int given = 0;
    while (puzzle[given] == 0) given++;
    check(!game_tap(&g, given) && g.selected == given, "verilen hucre secilmedi");
    check(!game_key(&g, other) && g.sticky == other && g.selected == GAME_NONE, "verilen hucre seciliyken tus kilitlemedi");
    check(!game_tap(&g, given) && g.value[given] == puzzle[given] && g.selected == GAME_NONE, "verilen hucre degisti");
    g.noteMode = 1;
    check(game_tap(&g, b) && g.notes[b] == sudoku_bit(other), "kilitli rakam not olarak yazilmadi");
    check(game_tap(&g, b) && g.notes[b] == 0, "kilitli not kapanmadi");
    g.noteMode = 0;
    check(!game_key(&g, other) && g.sticky == 0, "kilit acilmadi");
    check(!game_key(&g, d) && g.sticky == d, "tekrar kilitlenmedi");
    check(game_hint(&g, e) && g.sticky == 0 && g.selected == g.hintCell, "ipucu kilidi acmadi");
    game_select(&g, GAME_NONE);
    check(!game_key(&g, d) && g.sticky == d, "ipucudan sonra kilitlenmedi");
    for (int i = 0; i < 81; i++) if (g.value[i] == 0 && e->solution[i] == d) check(game_tap(&g, i), "rakam tamamlanirken yazilamadi");
    check(game_remaining(&g, d) == 0 && g.sticky == 0, "rakam tamamlaninca kilit acilmadi");
    check(!game_key(&g, d) && g.sticky == 0, "tamamlanan rakam kilitlendi");
    for (int i = 0; i < 81; i++) {
        if (g.value[i] != 0) continue;
        g.sticky = e->solution[i];
        check(game_tap(&g, i), "cozum kilitli yazilamadi");
    }
    check(g.solved && g.sticky == 0, "cozulunce kilit acilmadi");
    check(!game_tap(&g, a) && !game_key(&g, d) && g.sticky == 0, "cozulmus oyunda kilit calisti");
    game_free(&g);
    printf("Rakam once testleri gecti\n");
}

int main(int argc, char **argv) {
    int n = argc > 1 ? atoi(argv[1]) : 100;
    Sudoku engine;
    sudoku_init(&engine, 20260929);
    for (int level = 0; level < 4; level++) {
        double start = nowMs();
        int clues = 0;
        int techniques[7] = {0};
        for (int i = 0; i < n; i++) {
            int puzzle[81];
            sudoku_generate(&engine, level, puzzle);
            int solution[81];
            memcpy(solution, engine.solution, sizeof(solution));
            check(sudoku_count_solutions(&engine, puzzle, 2) == 1, "tek cozum yok");
            check(memcmp(engine.found, solution, sizeof(solution)) == 0, "cozum tam izgarayla eslesmiyor");
            int r = verifiedRate(&engine, puzzle, solution);
            int levelOk = level < 2 ? r == 0 : level == 2 ? r >= 1 && r <= 2 : r >= 3 && r <= 6;
            check(levelOk, "seviye derecesi yanlis");
            if (r >= 0 && r < 7) techniques[r]++;
            clues += count(puzzle);
            hintWalk(&engine, puzzle, solution);
        }
        double ms = nowMs() - start;
        printf("%-6s %4d bulmaca, ort ipucu %.1f, %5.1f ms/bulmaca, teknik dagilimi [%d, %d, %d, %d, %d, %d, %d]\n",
            LEVELS[level], n, clues / (double)n, ms / n, techniques[0], techniques[1], techniques[2], techniques[3], techniques[4], techniques[5], techniques[6]);
    }
    gameTests(&engine);
    stickyTests(&engine);
    printf("%s\n", failures == 0 ? "TAMAM" : "HATA");
    return failures == 0 ? 0 : 1;
}
