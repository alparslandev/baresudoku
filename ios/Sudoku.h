#ifndef SUDOKU_H
#define SUDOKU_H

#include <stdint.h>

#define SUDOKU_ALL 0x1FF

typedef struct Sudoku {
    uint64_t rng;
    int work[81];
    int count;
    int limit;
    int found[81];
    int hasFound;
    int lv[81];
    int lc[81];
    int stepCell;
    int stepDigit;
    int stepUnit;
    int positions[10];
    int lineMasks[9];
    int corners[4];
    int dist81[81];
    int queue81[81];
    int cellList[81];
    int quad[4];
    int others[9];
    int techLimit;
    int stepRating;
    int stepOrder;
    int rateOrder;
    int rateTech;
    int hintTech;
    int hintRating;
    int solution[81];
} Sudoku;

#define SUDOKU_TECH_COUNT 27
#define SUDOKU_MASTER_RATING 65
#define SUDOKU_LEVELS 4

extern const int SUDOKU_TECH_BASE[SUDOKU_TECH_COUNT];
extern int SUDOKU_TECH_ORDER[SUDOKU_TECH_COUNT];
extern int SUDOKU_EXPERT_LIMIT;

extern int SUDOKU_ROW[81];
extern int SUDOKU_COL[81];
extern int SUDOKU_BOX[81];
extern int SUDOKU_UNITS[27][9];
extern int SUDOKU_PEERS[81][20];

void sudoku_init(Sudoku *s, uint64_t seed);
int sudoku_sees(int a, int b);
int sudoku_bit(int digit);
int sudoku_digit(int singleBit);
int sudoku_candidates(const int *values, int cell);
int sudoku_count_solutions(Sudoku *s, const int *puzzle, int max);
void sudoku_full_grid(Sudoku *s, int *out);
void sudoku_dig(Sudoku *s, const int *full, int minClues, int *out);
void sudoku_load(Sudoku *s, const int *values);
int sudoku_complete(const Sudoku *s);
int sudoku_stuck(const Sudoku *s);
int sudoku_step(Sudoku *s);
int sudoku_rate(Sudoku *s, const int *puzzle);
int sudoku_hint(Sudoku *s, const int *values, const int *givens);
void sudoku_generate(Sudoku *s, int level, int *puzzle);

#endif
