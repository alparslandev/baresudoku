#ifndef GAME_H
#define GAME_H

#include <stdint.h>
#include "Sudoku.h"

#define GAME_NONE (-1)
#define HINT_NONE 0
#define HINT_WRONG 1
#define HINT_PLACE 2
#define GAME_COLORS 6

typedef struct Game {
    int given[81];
    int solution[81];
    int value[81];
    int notes[81];
    int corner[81];
    int color[81];
    int *hist;
    int histLen;
    int histCap;
    int *recStart;
    int histCount;
    int recCap;
    int record[324];
    int recordLength;
    int active;
    int solved;
    int noteMode;
    int cornerMode;
    int showErrors;
    int level;
    int rating;
    int selected;
    int sticky;
    int64_t elapsed;
    int64_t runningSince;
    int running;
    int hintKind;
    int hintCell;
    int hintDigit;
    int hintTech;
    int hintUnit;
    int hintMoves;
} Game;

void game_init(Game *g);
void game_free(Game *g);
void game_start(Game *g, const int *puzzle, const int *full, int level);
void game_restart(Game *g);
int game_can_edit(const Game *g);
void game_select(Game *g, int cell);
int game_enter(Game *g, int d);
int game_erase(Game *g);
void game_cycle_notes(Game *g);
int game_paint(Game *g, int k);
int game_undo(Game *g);
int game_fill_notes(Game *g);
int game_hint_active(const Game *g);
int game_hint(Game *g, Sudoku *engine);
int game_key(Game *g, int d);
int game_tap(Game *g, int cell);
int game_conflict(const Game *g, int cell);
int game_wrong(const Game *g, int cell);
int game_remaining(const Game *g, int d);
void game_resume(Game *g, int64_t now);
void game_pause(Game *g, int64_t now);
int64_t game_time(const Game *g, int64_t now);
char *game_encode(const Game *g, int64_t now);
int game_decode(Game *g, const char *text);

#endif
