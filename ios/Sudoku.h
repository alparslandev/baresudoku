#ifndef SUDOKU_H
#define SUDOKU_H

#include <stdint.h>

#define SUDOKU_ALL 0x1FF
#define SUDOKU_NODES 1215
#define SUDOKU_STATES (SUDOKU_NODES * 2)
#define SUDOKU_MAX_LINKS 131072
#define SUDOKU_MAX_ALS 1024
#define SUDOKU_MAX_ALS_LINKS 131072
#define SUDOKU_MAX_NEST 8
#define SUDOKU_TECH_COUNT 45
#define SUDOKU_MASTER_RATING 65
#define SUDOKU_LEVELS 5

typedef struct Sudoku {
    uint32_t rng;
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
    int unitPos[243];
    int digitCells[27];
    int groupCount;
    int groupFirst[11];
    int groupDigit[486];
    int groupCells[1458];
    int groupSize[486];
    int groupBox[486];
    int groupLine[486];
    int groupSeen[1458];
    int groupAt[486];
    int linkStart[SUDOKU_STATES + 1];
    int linkTo[SUDOKU_MAX_LINKS];
    int mark[SUDOKU_STATES];
    int markValue;
    int depth[SUDOKU_STATES];
    int parent[SUDOKU_STATES];
    int queue[SUDOKU_STATES];
    int elimTry[81];
    int elimBest[81];
    int inter[3];
    int lineRest[6];
    int boxRest[6];
    int lineUnion[64];
    int boxUnion[64];
    int alsCount;
    int alsDigits[SUDOKU_MAX_ALS];
    int alsCells[SUDOKU_MAX_ALS * 3];
    int alsDigitCells[SUDOKU_MAX_ALS * 27];
    int alsSeen[SUDOKU_MAX_ALS * 27];
    int alsLinkStart[SUDOKU_MAX_ALS + 1];
    int alsLinkTo[SUDOKU_MAX_ALS_LINKS];
    int alsLinkMask[SUDOKU_MAX_ALS_LINKS];
    int petals[SUDOKU_MAX_ALS * 9];
    int petalStart[10];
    int alsMark[SUDOKU_MAX_ALS * 9];
    int alsMarkValue;
    int alsFirst[SUDOKU_MAX_ALS * 9];
    int alsDepth[SUDOKU_MAX_ALS * 9];
    int alsQueue[SUDOKU_MAX_ALS * 9];
    int offMask[81];
    int onMask[81];
    int unionMask[81];
    int sv[81 * (SUDOKU_MAX_NEST + 1)];
    int sc[81 * (SUDOKU_MAX_NEST + 1)];
    int techLimit;
    unsigned char techOff[SUDOKU_TECH_COUNT];
    int stepRating;
    int stepOrder;
    int rateOrder;
    int rateTech;
    int hintTech;
    int hintRating;
    int rating;
    int solution[81];
} Sudoku;


extern const int SUDOKU_TECH_BASE[SUDOKU_TECH_COUNT];
extern int SUDOKU_TECH_ORDER[SUDOKU_TECH_COUNT];
extern int SUDOKU_EXPERT_LIMIT;

extern int SUDOKU_ROW[81];
extern int SUDOKU_COL[81];
extern int SUDOKU_BOX[81];
extern int SUDOKU_UNITS[27][9];
extern int SUDOKU_PEERS[81][20];

void sudoku_init(Sudoku *s, uint64_t seed);
void sudoku_seed(Sudoku *s, int seed);
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
