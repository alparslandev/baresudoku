#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "../Sudoku.h"
#include "../Game.h"

static int failures;
static const char *LEVEL_NAMES[] = {"Kolay", "Orta", "Zor", "Uzman", "Usta"};

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
        check(e->stepRating >= SUDOKU_TECH_BASE[t], "adim derecesi taban altinda");
        if (e->stepRating > max) max = e->stepRating;
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
        check(sudoku_hint(e, values, puzzle), "ipucu bulunamadi");
        if (values[e->stepCell] != 0) {
            check(0, "ipucu dolu hucreye geldi");
            return;
        }
        check(solution[e->stepCell] == e->stepDigit, "ipucu rakami yanlis");
        check(e->hintTech >= 0 && e->hintTech < SUDOKU_TECH_COUNT, "ipucu teknigi aralik disi");
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

static void cornerTests(Sudoku *e) {
    int puzzle[81];
    sudoku_generate(e, 1, puzzle);
    Game g;
    game_init(&g);
    game_start(&g, puzzle, e->solution, 1);
    int cell = firstEmpty(puzzle);
    int d = e->solution[cell];
    int peer = -1;
    for (int k = 0; k < 20 && peer < 0; k++) if (puzzle[SUDOKU_PEERS[cell][k]] == 0) peer = SUDOKU_PEERS[cell][k];
    game_cycle_notes(&g);
    game_cycle_notes(&g);
    check(g.noteMode && g.cornerMode, "not dongusu koseye gelmedi");
    game_select(&g, peer);
    check(game_enter(&g, d) && g.corner[peer] == sudoku_bit(d) && g.notes[peer] == 0, "kose notu yazilmadi");
    game_cycle_notes(&g);
    check(!g.noteMode && !g.cornerMode, "not dongusu kapanmadi");
    game_select(&g, cell);
    check(game_enter(&g, d) && g.corner[peer] == 0, "yerlestirince kose notu eslerden silinmedi");
    check(game_undo(&g) && g.value[cell] == 0 && g.corner[peer] == sudoku_bit(d), "geri al kose notunu donmedi");
    game_select(&g, peer);
    check(game_erase(&g) && g.corner[peer] == 0, "sil kose notunu silmedi");
    check(game_undo(&g) && g.corner[peer] == sudoku_bit(d), "silmeyi geri al kose notunu donmedi");
    check(game_paint(&g, 3) && g.color[peer] == 3 && game_paint(&g, 3) && g.color[peer] == 0, "boya acilip kapanmadi");
    check(game_paint(&g, 5), "boya calismadi");
    char *saved = game_encode(&g, 0);
    Game h;
    game_init(&h);
    check(game_decode(&h, saved), "yeni kayit okunmadi");
    check(memcmp(h.corner, g.corner, sizeof(g.corner)) == 0 && memcmp(h.color, g.color, sizeof(g.color)) == 0, "kose ve renk kayittan donmedi");
    check(h.histCount == g.histCount && h.histLen == g.histLen && memcmp(h.hist, g.hist, sizeof(int) * (size_t)g.histLen) == 0, "gecmis kayittan farkli dondu");
    char *copy = strdup(saved);
    char *fields[4096];
    int nf = 0;
    char *cur = copy, *tok;
    while ((tok = strsep(&cur, "|")) != NULL && nf < 4096) fields[nf++] = tok;
    int count = atoi(fields[11]);
    char *old = malloc(strlen(saved) + 1);
    char *w = old;
    for (int k = 0; k < 12; k++) w += sprintf(w, "%s%s", k ? "|" : "", fields[k]);
    for (int k = 0; k < count; k++) {
        int values[324];
        int len = 0;
        char *rec = fields[12 + k], *part;
        while ((part = strsep(&rec, ",")) != NULL) values[len++] = atoi(part);
        *w++ = '|';
        for (int i = 0; i < len; i += 4) w += sprintf(w, "%s%d,%d,%d", i ? "," : "", values[i], values[i + 1], values[i + 2]);
    }
    *w = 0;
    Game k2;
    game_init(&k2);
    check(game_decode(&k2, old) && k2.histCount == count, "eski kayit okunmadi");
    for (int i = 0; i < 81; i++) check(k2.corner[i] == 0 && k2.color[i] == 0, "eski kayitta kose ya da renk dolu");
    check(k2.histLen % 4 == 0, "eski kayit gecmisi dortlu degil");
    game_free(&k2);
    free(old);
    free(copy);
    game_free(&h);
    free(saved);
    game_restart(&g);
    for (int i = 0; i < 81; i++) check(g.corner[i] == 0 && g.color[i] == 0, "bastan basla kose ve rengi silmedi");
    game_free(&g);
    printf("Kose notu ve renk testleri gecti\n");
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

static int digitsEqual(const int *puzzle, const char *expected) {
    for (int i = 0; i < 81; i++) if (puzzle[i] != expected[i] - '0') return 0;
    return 1;
}

static void seedTests(void) {
    static Sudoku seeded;
    int puzzle[81];
    sudoku_init(&seeded, 162088025);
    sudoku_generate(&seeded, 1, puzzle);
    check(digitsEqual(puzzle, "000020006000004700680100009004070068003000900250040100300002087007400000100030000") && seeded.rating == 10, "tohumlu uretim (Orta) beklenen bulmaca degil");
    sudoku_seed(&seeded, 162088027);
    sudoku_generate(&seeded, 3, puzzle);
    check(digitsEqual(puzzle, "300600480000320600091000002000060007009705800500040000900000310003096000018004006") && seeded.rating == 40, "tohumlu uretim (Uzman) beklenen bulmaca degil");
    printf("Tohumlu uretim testleri gecti\n");
}

static void registryTests(void) {
    int seen[SUDOKU_TECH_COUNT] = {0};
    for (int k = 0; k < SUDOKU_TECH_COUNT; k++) {
        int id = SUDOKU_TECH_ORDER[k];
        check(!seen[id], "teknik sirasinda tekrar");
        seen[id] = 1;
        if (k < 3) check(id == k, "ilk uc teknik yerinde degil");
        if (k > 3) {
            int prev = SUDOKU_TECH_ORDER[k - 1];
            check(SUDOKU_TECH_BASE[prev] < SUDOKU_TECH_BASE[id] || (SUDOKU_TECH_BASE[prev] == SUDOKU_TECH_BASE[id] && prev < id), "teknik sirasi dereceye gore degil");
        }
        check((SUDOKU_TECH_BASE[id] < SUDOKU_MASTER_RATING) == (k < SUDOKU_EXPERT_LIMIT), "uzman siniri yanlis");
    }
    check((int)(sizeof(LEVEL_NAMES) / sizeof(LEVEL_NAMES[0])) >= SUDOKU_LEVELS, "seviye adi eksik");
}

static int unitsOk(const Variant *v, const int *grid) {
    const VariantShape *s = &v->shape;
    for (int u = 0; u < s->unitCount; u++) {
        int m = 0;
        for (int k = 0; k < s->n; k++) m |= 1 << (grid[s->unitCells[u * 9 + k]] - 1);
        if (m != s->all) return 0;
    }
    for (int k = 0; k < s->cageCount; k++) {
        int m = 0, sum = 0;
        for (int i = 0; i < s->cageSize[k]; i++) {
            int d = grid[s->cageCells[k * 9 + i]];
            if ((m & (1 << (d - 1))) != 0) return 0;
            m |= 1 << (d - 1);
            sum += d;
        }
        if (sum != s->cageSum[k]) return 0;
    }
    return 1;
}

static Variant variantA;
static Variant variantB;

static void variantTests(void) {
    for (int kind = 0; kind < 3; kind++) {
        for (int level = 0; level < VARIANT_LEVELS; level++) {
            Variant *v = &variantA;
            variant_init(v, kind, (uint32_t)(1000 + level));
            int puzzle[81] = {0};
            variant_generate(v, level, puzzle);
            int size = v->shape.size;
            int solution[81] = {0};
            memcpy(solution, v->solution, sizeof(int) * (size_t)size);
            check(variant_count_solutions(v, puzzle, 2) == 1, "varyant tek cozumlu degil");
            check(memcmp(v->found, solution, sizeof(int) * (size_t)size) == 0, "varyant cozumu tam izgarayla eslesmiyor");
            check(unitsOk(v, solution), "varyant cozumu birim ya da kafes kuralini bozuyor");
            for (int c = 0; c < size; c++) if (puzzle[c] != 0) check(puzzle[c] == solution[c], "varyant verileni cozumle uyusmuyor");
            int again[81] = {0};
            variant_init(&variantB, kind, (uint32_t)(1000 + level));
            variant_generate(&variantB, level, again);
            check(memcmp(again, puzzle, sizeof(int) * (size_t)size) == 0, "ayni tohum farkli varyant bulmacasi");
        }
        Variant *v = &variantA;
        variant_init(v, kind, 77);
        int puzzle[81] = {0};
        variant_generate(v, 1, puzzle);
        int size = v->shape.size;
        int solution[81] = {0};
        memcpy(solution, v->solution, sizeof(int) * (size_t)size);
        Game g;
        game_init(&g);
        game_set_shape(&g, &v->shape);
        game_start(&g, puzzle, solution, 1);
        int ones = 0;
        for (int c = 0; c < size; c++) if (puzzle[c] == 1) ones++;
        check(game_remaining(&g, 1) == v->shape.n - ones, "varyant kalan sayisi");
        int cell = 0;
        while (puzzle[cell] != 0) cell++;
        int peer = -1;
        for (int k = 0; k < g.peerCount[cell] && peer < 0; k++) if (puzzle[g.peers[cell][k]] != 0) peer = g.peers[cell][k];
        game_select(&g, cell);
        check(game_enter(&g, puzzle[peer]) && game_conflict(&g, cell), "varyant cakismasi eslere gore degil");
        check(game_undo(&g), "varyant geri alma");
        check(!game_key(&g, v->shape.n + 1), "alan disi rakam kabul edildi");
        check(game_fill_notes(&g), "varyant notlari doldurulmadi");
        for (int c = 0; c < size; c++) if (g.value[c] == 0) check((g.notes[c] & sudoku_bit(solution[c])) != 0, "varyant notunda dogru rakam yok");
        for (int steps = 0; !g.solved && steps < 200; steps++) {
            check(game_hint_variant(&g, v), "varyant ipucu bulunamadi");
            check(game_hint_variant(&g, v), "varyant ipucu yerlestirmedi");
        }
        check(g.solved, "varyant ipucu yolu bulmacayi cozmedi");
        char *saved = game_encode(&g, 0);
        Game h;
        game_init(&h);
        game_set_shape(&h, &v->shape);
        check(game_decode(&h, saved) && memcmp(h.value, solution, sizeof(int) * (size_t)size) == 0, "varyant kaydi geri yuklenmedi");
        Game classic;
        game_init(&classic);
        if (size != 81) check(!game_decode(&classic, saved), "6x6 kaydi klasik oyuna yuklendi");
        game_free(&classic);
        game_free(&h);
        game_free(&g);
        free(saved);
    }
    printf("Varyant testleri gecti\n");
}

static Sudoku engine;

int main(int argc, char **argv) {
    int n = argc > 1 ? atoi(argv[1]) : 100;
    sudoku_init(&engine, 20260929);
    registryTests();
    seedTests();
    for (int level = 0; level < SUDOKU_LEVELS; level++) {
        double start = nowMs();
        int clues = 0;
        int techniques[SUDOKU_TECH_COUNT] = {0};
        for (int i = 0; i < n; i++) {
            int puzzle[81];
            sudoku_generate(&engine, level, puzzle);
            int solution[81];
            memcpy(solution, engine.solution, sizeof(solution));
            check(sudoku_count_solutions(&engine, puzzle, 2) == 1, "tek cozum yok");
            check(memcmp(engine.found, solution, sizeof(solution)) == 0, "cozum tam izgarayla eslesmiyor");
            int r = verifiedRate(&engine, puzzle, solution);
            check(sudoku_rate(&engine, puzzle) == r, "derece tekrarinda farkli");
            int order = engine.rateOrder;
            int levelOk = level < 2 ? r == SUDOKU_TECH_BASE[0] : level == 2 ? order >= 1 && order <= 2
                : level == 3 ? order >= 3 && r < SUDOKU_MASTER_RATING : r >= SUDOKU_MASTER_RATING;
            check(levelOk, "seviye derecesi yanlis");
            techniques[engine.rateTech]++;
            clues += count(puzzle);
            hintWalk(&engine, puzzle, solution);
        }
        double ms = nowMs() - start;
        printf("%-6s %4d bulmaca, ort ipucu %.1f, %5.1f ms/bulmaca, teknik dagilimi [", LEVEL_NAMES[level], n, clues / (double)n, ms / n);
        for (int k = 0; k < SUDOKU_TECH_COUNT; k++) printf(k ? ", %d" : "%d", techniques[k]);
        printf("]\n");
    }
    gameTests(&engine);
    stickyTests(&engine);
    cornerTests(&engine);
    variantTests();
    printf("%s\n", failures == 0 ? "TAMAM" : "HATA");
    return failures == 0 ? 0 : 1;
}
