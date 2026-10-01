#include "Game.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void game_init(Game *g) {
    memset(g, 0, sizeof(*g));
    g->showErrors = 1;
    g->selected = GAME_NONE;
    g->hintCell = GAME_NONE;
}

void game_free(Game *g) {
    free(g->hist);
    free(g->recStart);
    g->hist = NULL;
    g->recStart = NULL;
    g->histLen = g->histCap = g->histCount = g->recCap = 0;
}

static void clearHistory(Game *g) {
    g->histLen = 0;
    g->histCount = 0;
    g->recordLength = 0;
}

void game_restart(Game *g) {
    memcpy(g->value, g->given, sizeof(g->value));
    memset(g->notes, 0, sizeof(g->notes));
    clearHistory(g);
    g->solved = 0;
    g->noteMode = 0;
    g->selected = GAME_NONE;
    g->sticky = 0;
    g->elapsed = 0;
    g->running = 0;
    g->hintKind = HINT_NONE;
}

void game_start(Game *g, const int *puzzle, const int *full, int level) {
    memcpy(g->given, puzzle, sizeof(g->given));
    memcpy(g->solution, full, sizeof(g->solution));
    g->level = level;
    g->active = 1;
    game_restart(g);
}

int game_can_edit(const Game *g) {
    return g->active && !g->solved && g->selected >= 0 && g->given[g->selected] == 0;
}

void game_select(Game *g, int cell) {
    if (g->sticky == 0) g->selected = cell;
}

static void touch(Game *g, int cell) {
    for (int i = 0; i < g->recordLength; i += 3) if (g->record[i] == cell) return;
    g->record[g->recordLength++] = cell;
    g->record[g->recordLength++] = g->value[cell];
    g->record[g->recordLength++] = g->notes[cell];
}

static void pushRecord(Game *g, const int *values, int n) {
    if (g->histCount + 1 >= g->recCap) {
        g->recCap = g->recCap ? g->recCap * 2 : 64;
        g->recStart = realloc(g->recStart, sizeof(int) * (size_t)(g->recCap + 1));
    }
    if (g->histLen + n > g->histCap) {
        while (g->histLen + n > g->histCap) g->histCap = g->histCap ? g->histCap * 2 : 1024;
        g->hist = realloc(g->hist, sizeof(int) * (size_t)g->histCap);
    }
    g->recStart[g->histCount] = g->histLen;
    memcpy(g->hist + g->histLen, values, sizeof(int) * (size_t)n);
    g->histLen += n;
    g->histCount++;
}

static int commit(Game *g) {
    if (g->recordLength == 0) return 0;
    pushRecord(g, g->record, g->recordLength);
    g->recordLength = 0;
    return 1;
}

static void checkSolved(Game *g) {
    for (int i = 0; i < 81; i++) if (g->value[i] != g->solution[i]) return;
    g->solved = 1;
    g->selected = GAME_NONE;
}

int game_remaining(const Game *g, int d) {
    int n = 9;
    for (int i = 0; i < 81; i++) if (g->value[i] == d) n--;
    return n;
}

int game_enter(Game *g, int d) {
    if (!game_can_edit(g)) return 0;
    int c = g->selected;
    if (g->noteMode) {
        if (g->value[c] != 0) return 0;
        touch(g, c);
        g->notes[c] ^= sudoku_bit(d);
        return commit(g);
    }
    touch(g, c);
    if (g->value[c] == d) {
        g->value[c] = 0;
        return commit(g);
    }
    g->value[c] = d;
    g->notes[c] = 0;
    int b = sudoku_bit(d);
    for (int k = 0; k < 20; k++) {
        int p = SUDOKU_PEERS[c][k];
        if ((g->notes[p] & b) != 0) {
            touch(g, p);
            g->notes[p] &= ~b;
        }
    }
    commit(g);
    checkSolved(g);
    if (g->sticky != 0 && (g->solved || game_remaining(g, g->sticky) <= 0)) g->sticky = 0;
    return 1;
}

int game_key(Game *g, int d) {
    if (g->sticky == 0 && game_can_edit(g) && (g->noteMode ? g->value[g->selected] == 0 : g->value[g->selected] != d)) return game_enter(g, d);
    g->sticky = g->sticky == d || game_remaining(g, d) <= 0 ? 0 : d;
    if (g->sticky != 0) g->selected = GAME_NONE;
    return 0;
}

int game_tap(Game *g, int cell) {
    if (!g->active || g->solved) return 0;
    if (g->sticky == 0) {
        g->selected = cell == g->selected ? GAME_NONE : cell;
        return 0;
    }
    g->selected = cell;
    int changed = game_enter(g, g->sticky);
    g->selected = GAME_NONE;
    return changed;
}

int game_erase(Game *g) {
    if (!game_can_edit(g) || (g->value[g->selected] == 0 && g->notes[g->selected] == 0)) return 0;
    touch(g, g->selected);
    g->value[g->selected] = 0;
    g->notes[g->selected] = 0;
    return commit(g);
}

int game_conflict(const Game *g, int cell) {
    int v = g->value[cell];
    if (v == 0) return 0;
    for (int k = 0; k < 20; k++) if (g->value[SUDOKU_PEERS[cell][k]] == v) return 1;
    return 0;
}

int game_wrong(const Game *g, int cell) {
    return g->showErrors && g->given[cell] == 0 && g->value[cell] != 0 && g->value[cell] != g->solution[cell];
}

void game_resume(Game *g, int64_t now) {
    if (g->active && !g->solved && !g->running) {
        g->running = 1;
        g->runningSince = now;
    }
}

void game_pause(Game *g, int64_t now) {
    if (g->running) {
        g->elapsed += now - g->runningSince;
        g->running = 0;
    }
}

int64_t game_time(const Game *g, int64_t now) {
    return g->elapsed + (g->running ? now - g->runningSince : 0);
}

int game_undo(Game *g) {
    if (!g->active || g->solved || g->histCount == 0) return 0;
    int start = g->recStart[g->histCount - 1];
    const int *r = g->hist + start;
    int n = g->histLen - start;
    for (int i = 0; i < n; i += 3) {
        g->value[r[i]] = r[i + 1];
        g->notes[r[i]] = r[i + 2];
    }
    if (g->sticky == 0) g->selected = r[0];
    g->histLen = start;
    g->histCount--;
    return 1;
}

int game_fill_notes(Game *g) {
    if (!g->active || g->solved) return 0;
    for (int i = 0; i < 81; i++) {
        if (g->value[i] != 0) continue;
        int m = sudoku_candidates(g->value, i);
        if (g->notes[i] != m) {
            touch(g, i);
            g->notes[i] = m;
        }
    }
    if (commit(g)) return 1;
    for (int i = 0; i < 81; i++) {
        if (g->value[i] == 0 && g->notes[i] != 0) {
            touch(g, i);
            g->notes[i] = 0;
        }
    }
    return commit(g);
}

int game_hint_active(const Game *g) {
    return g->hintKind != HINT_NONE && g->active && !g->solved && g->histCount == g->hintMoves && g->selected == g->hintCell;
}

int game_hint(Game *g, Sudoku *engine) {
    if (!g->active || g->solved) return 0;
    g->sticky = 0;
    if (game_hint_active(g) && g->hintKind == HINT_PLACE && g->value[g->hintCell] == 0) {
        g->noteMode = 0;
        g->hintKind = HINT_NONE;
        return game_enter(g, g->hintDigit);
    }
    g->hintKind = HINT_NONE;
    for (int i = 0; i < 81; i++) {
        if (g->given[i] == 0 && g->value[i] != 0 && g->value[i] != g->solution[i]) {
            g->hintKind = HINT_WRONG;
            g->hintCell = i;
            g->selected = i;
            g->hintMoves = g->histCount;
            return 1;
        }
    }
    if (!sudoku_hint(engine, g->value, g->given)) return 0;
    g->hintKind = HINT_PLACE;
    g->hintCell = engine->stepCell;
    g->hintDigit = engine->stepDigit;
    g->hintTech = engine->hintTech;
    g->hintUnit = engine->stepUnit;
    g->selected = g->hintCell;
    g->hintMoves = g->histCount;
    return 1;
}

static void appendDigits(char **p, const int *a) {
    for (int i = 0; i < 81; i++) *(*p)++ = (char)('0' + a[i]);
}

char *game_encode(const Game *g, int64_t now) {
    size_t cap = 800 + (size_t)g->histLen * 5 + (size_t)g->histCount * 2;
    char *out = malloc(cap);
    char *p = out;
    p += sprintf(p, "%d|%d|%d|%d|%d|%d|%lld|", g->active ? 1 : 0, g->level, g->solved ? 1 : 0,
        g->showErrors ? 1 : 0, g->noteMode ? 1 : 0, g->selected, (long long)game_time(g, now));
    appendDigits(&p, g->given);
    *p++ = '|';
    appendDigits(&p, g->solution);
    *p++ = '|';
    appendDigits(&p, g->value);
    *p++ = '|';
    for (int i = 0; i < 81; i++) {
        int m = g->notes[i];
        *p++ = (char)('0' + (m >> 6));
        *p++ = (char)('0' + ((m >> 3) & 7));
        *p++ = (char)('0' + (m & 7));
    }
    p += sprintf(p, "|%d", g->histCount);
    for (int r = 0; r < g->histCount; r++) {
        int start = g->recStart[r];
        int end = r + 1 < g->histCount ? g->recStart[r + 1] : g->histLen;
        *p++ = '|';
        for (int i = start; i < end; i++) {
            if (i > start) *p++ = ',';
            p += sprintf(p, "%d", g->hist[i]);
        }
    }
    *p = 0;
    return out;
}

static int parseInt(const char *s, int *out) {
    if (*s == 0) return 0;
    char *end;
    long v = strtol(s, &end, 10);
    if (*end != 0 || v < -1000000 || v > 1000000) return 0;
    *out = (int)v;
    return 1;
}

static int readDigits(const char *s, int *a) {
    if (strlen(s) != 81) return 0;
    for (int i = 0; i < 81; i++) {
        int v = s[i] - '0';
        if (v < 0 || v > 9) return 0;
        a[i] = v;
    }
    return 1;
}

int game_decode(Game *g, const char *text) {
    g->active = 0;
    if (text == NULL) return 0;
    char *copy = strdup(text);
    int fieldCount = 1;
    for (const char *c = text; *c; c++) if (*c == '|') fieldCount++;
    char **f = malloc(sizeof(char *) * (size_t)fieldCount);
    char *cursor = copy;
    int n = 0;
    char *tok;
    while ((tok = strsep(&cursor, "|")) != NULL) f[n++] = tok;
    int ok = 0;
    int lvl = 0, sel = 0, count = 0;
    long long time = 0;
    if (n < 12) goto done;
    g->showErrors = strcmp(f[3], "1") == 0;
    if (strcmp(f[0], "1") != 0) {
        ok = 1;
        goto done;
    }
    if (!parseInt(f[1], &lvl) || !parseInt(f[5], &sel)) goto done;
    {
        char *end;
        time = strtoll(f[6], &end, 10);
        if (*f[6] == 0 || *end != 0) goto done;
    }
    if (lvl < 0 || lvl >= SUDOKU_LEVELS || sel < GAME_NONE || sel > 80 || time < 0) goto done;
    if (!readDigits(f[7], g->given) || !readDigits(f[8], g->solution) || !readDigits(f[9], g->value)) goto done;
    if (strlen(f[10]) != 243) goto done;
    for (int i = 0; i < 81; i++) {
        int m = 0;
        for (int k = 0; k < 3; k++) {
            int o = f[10][i * 3 + k] - '0';
            if (o < 0 || o > 7) goto done;
            m = (m << 3) | o;
        }
        g->notes[i] = m;
    }
    if (!parseInt(f[11], &count) || count < 0 || n != 12 + count) goto done;
    clearHistory(g);
    for (int k = 0; k < count; k++) {
        int values[243];
        int len = 0;
        char *rec = f[12 + k];
        char *part;
        while ((part = strsep(&rec, ",")) != NULL) {
            int v;
            if (len >= 243 || !parseInt(part, &v)) goto done;
            values[len++] = v;
        }
        if (len == 0 || len % 3 != 0) goto done;
        for (int i = 0; i < len; i += 3) {
            if (values[i] < 0 || values[i] > 80 || values[i + 1] < 0 || values[i + 1] > 9 || values[i + 2] < 0 || values[i + 2] > SUDOKU_ALL) goto done;
        }
        pushRecord(g, values, len);
    }
    g->recordLength = 0;
    g->level = lvl;
    g->solved = strcmp(f[2], "1") == 0;
    g->noteMode = strcmp(f[4], "1") == 0;
    g->selected = sel;
    g->sticky = 0;
    g->elapsed = time;
    g->running = 0;
    g->hintKind = HINT_NONE;
    g->active = 1;
    ok = 1;
done:
    if (!ok) {
        g->active = 0;
        clearHistory(g);
    }
    free(f);
    free(copy);
    return ok;
}
