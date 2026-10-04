#import <UIKit/UIKit.h>
#include <string.h>
#include <time.h>
#include "Sudoku.h"
#include "Game.h"
#include "Variant.h"

enum { S_UNDO = 4, S_ERASE, S_NOTE, S_FILL, S_HINT, S_NEW, S_ERRORS, S_ON, S_OFF, S_CANCEL, S_SOLVED, S_PREPARING,
    S_WRONG, S_NAKED, S_ROW, S_COL, S_BOX, S_AGAIN, S_TECH, S_TITLE = 28, S_RESTART = 29, S_ROWLABEL = 30, S_COLLABEL = 31, S_LEFT = 32, S_TECH_EXTRA = 33,
    S_MASTER = S_TECH_EXTRA + SUDOKU_TECH_COUNT - 7, S_DAILY, S_SHARE, S_PLAY, S_STATS, S_PLAYED, S_BEST, S_AVERAGE, S_STREAK,
    S_EXPLAIN, S_TIMER, S_COLOR, S_CORNER, S_VARIANTS, S_DIAG, S_REVEAL, S_CAGE, S_VARIANT_NAME };
enum { IDLE_MS = 60000, MENU_MAX = 12, TOOLS = 6 };
static const uint32_t PALETTE_LIGHT[7] = {0, 0xFFFFF1A8, 0xFFC8EFC4, 0xFFC4E0FF, 0xFFFFD0E6, 0xFFFFD6AC, 0xFFDDD0FF};
static const uint32_t PALETTE_DARK[7] = {0, 0xFF4D4418, 0xFF1F4526, 0xFF1D3A59, 0xFF55223D, 0xFF573616, 0xFF3A2C59};
static const int VARIANT_ORDER[3] = {VARIANT_KIND_KILLER, VARIANT_KIND_DIAGONAL, VARIANT_KIND_MINI};
static NSString *const VARIANT_PATHS[3] = {@"diagonal/", @"killer/", @"mini/"};

static NSString *const EN[] = {@"Easy", @"Medium", @"Hard", @"Expert", @"Undo", @"Erase", @"Notes", @"Fill notes", @"Hint",
    @"New game", @"Show mistakes", @"On", @"Off", @"Cancel", @"Solved!", @"Preparing…", @"This digit is wrong",
    @"Only one candidate here: #", @"Only place for # in this row", @"Only place for # in this column",
    @"Only place for # in this box", @"Tap hint again to place it", @"Locked candidates", @"Pair or triple",
    @"X-Wing", @"Y-Wing", @"Swordfish", @"XYZ-Wing", @"Bare Sudoku", @"Restart", @"Row", @"Column", @"# left",
    @"Skyscraper", @"2-String Kite", @"W-Wing", @"Unique Rectangle",
    @"Naked Quad", @"Hidden Quad", @"Jellyfish", @"Finned X-Wing", @"Finned Swordfish", @"Finned Jellyfish",
    @"Empty Rectangle", @"Remote Pair", @"WXYZ-Wing", @"Unique Rectangle Type 2", @"Unique Rectangle Type 3",
    @"Unique Rectangle Type 4", @"Unique Rectangle Type 5", @"Unique Rectangle Type 6", @"Hidden Rectangle", @"BUG+1",
    @"X-Chain", @"XY-Chain", @"Continuous Nice Loop", @"AIC", @"Grouped AIC", @"Sue de Coq", @"ALS-XZ",
    @"ALS-XY-Wing", @"Death Blossom", @"ALS Chain",
    @"Nishio Forcing Chain", @"Cell Forcing Chain", @"Unit Forcing Chain", @"Dynamic Forcing Net",
    @"Nested Forcing Net", @"Sashimi X-Wing", @"Sashimi Swordfish", @"Sashimi Jellyfish", @"Master",
    @"Daily Sudoku", @"Share", @"Play Sudoku", @"Statistics", @"Solved", @"Best time", @"Average time", @"Daily streak",
    @"Explain", @"Show timer", @"Color", @"Corner", @"Variants", @"Only place for # in this diagonal", @"This cell is #",
    @"Cage sum: #", @"Diagonal Sudoku", @"Killer Sudoku", @"Mini Sudoku 6\u00d76"};
static NSString *const TR[] = {@"Kolay", @"Orta", @"Zor", @"Uzman", @"Geri al", @"Sil", @"Not", @"Notları doldur", @"İpucu",
    @"Yeni oyun", @"Yanlışları göster", @"Açık", @"Kapalı", @"Vazgeç", @"Tebrikler!", @"Hazırlanıyor…", @"Bu rakam yanlış",
    @"Bu hücrede tek aday: #", @"Bu satırda # için tek yer", @"Bu sütunda # için tek yer",
    @"Bu kutuda # için tek yer", @"Yerleştirmek için ipucuna tekrar bas", @"Kilitli adaylar", @"Çift veya üçlü",
    @"X-Wing", @"Y-Wing", @"Swordfish", @"XYZ-Wing", @"Bare Sudoku", @"Baştan başla", @"Satır", @"Sütun", @"# kaldı",
    @"Skyscraper", @"2-String Kite", @"W-Wing", @"Unique Rectangle",
    @"Naked Quad", @"Hidden Quad", @"Jellyfish", @"Finned X-Wing", @"Finned Swordfish", @"Finned Jellyfish",
    @"Empty Rectangle", @"Remote Pair", @"WXYZ-Wing", @"Unique Rectangle Type 2", @"Unique Rectangle Type 3",
    @"Unique Rectangle Type 4", @"Unique Rectangle Type 5", @"Unique Rectangle Type 6", @"Hidden Rectangle", @"BUG+1",
    @"X-Chain", @"XY-Chain", @"Continuous Nice Loop", @"AIC", @"Grouped AIC", @"Sue de Coq", @"ALS-XZ",
    @"ALS-XY-Wing", @"Death Blossom", @"ALS Chain",
    @"Nishio Forcing Chain", @"Cell Forcing Chain", @"Unit Forcing Chain", @"Dynamic Forcing Net",
    @"Nested Forcing Net", @"Sashimi X-Wing", @"Sashimi Swordfish", @"Sashimi Jellyfish", @"Usta",
    @"Günlük Sudoku", @"Paylaş", @"Sudoku oyna", @"İstatistik", @"Çözülen", @"En iyi süre", @"Ortalama süre", @"Günlük seri",
    @"Açıkla", @"Zamanlayıcıyı göster", @"Renk", @"Köşe", @"Varyantlar", @"Bu köşegende # için tek yer", @"Bu hücre #",
    @"Kafes toplamı: #", @"Diagonal Sudoku", @"Killer Sudoku", @"Mini Sudoku 6\u00d76"};
static NSString *const DIGITS[] = {@"", @"1", @"2", @"3", @"4", @"5", @"6", @"7", @"8", @"9"};

static Game game;
static Sudoku engine;
static Variant *variant;
static int menuKind = -1;
static int pendingLevel = -1;
static NSString *dailyDate = @"";
static BOOL dailyMenu;
static BOOL showTimer = YES;
static BOOL recorded;
static NSString *const SITE = @"https://baresudoku.com/";

static int64_t nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static NSString *cageText(const VariantShape *shape) {
    NSMutableArray *cells = [NSMutableArray array], *sums = [NSMutableArray array];
    for (int c = 0; c < shape->size; c++) [cells addObject:@(shape->cageOf[c]).stringValue];
    for (int k = 0; k < shape->cageCount; k++) [sums addObject:@(shape->cageSum[k]).stringValue];
    return [NSString stringWithFormat:@"%@;%@", [cells componentsJoinedByString:@","], [sums componentsJoinedByString:@","]];
}

static int readCages(VariantShape *shape, NSString *text) {
    NSArray *parts = [text componentsSeparatedByString:@";"];
    if (parts.count != 2) return 0;
    NSArray *cells = [parts[0] componentsSeparatedByString:@","], *sums = [parts[1] componentsSeparatedByString:@","];
    if ((int)cells.count != shape->size || sums.count < 1 || sums.count > VARIANT_MAX_CAGES) return 0;
    int count = (int)sums.count;
    int cageOf[VARIANT_MAX_CELLS], cageSum[VARIANT_MAX_CAGES], members[VARIANT_MAX_CAGES] = {0};
    NSCharacterSet *digits = [NSCharacterSet characterSetWithCharactersInString:@"-0123456789"];
    for (int k = 0; k < count; k++) {
        NSString *s = sums[k];
        if (s.length == 0 || [s stringByTrimmingCharactersInSet:digits].length) return 0;
        cageSum[k] = s.intValue;
        if (cageSum[k] < 1 || cageSum[k] > 45) return 0;
    }
    for (int c = 0; c < shape->size; c++) {
        NSString *s = cells[c];
        if (s.length == 0 || [s stringByTrimmingCharactersInSet:digits].length) return 0;
        cageOf[c] = s.intValue;
        if (cageOf[c] < -1 || cageOf[c] >= count) return 0;
        if (cageOf[c] >= 0 && ++members[cageOf[c]] > 9) return 0;
    }
    for (int k = 0; k < count; k++) if (members[k] == 0) return 0;
    variant_set_cages(shape, cageOf, cageSum, count);
    return 1;
}

static void useVariant(Variant *next) {
    if (variant != next) free(variant);
    variant = next;
    game_set_shape(&game, next ? &next->shape : NULL);
}

static BOOL restoreVariant(NSUserDefaults *defaults) {
    useVariant(NULL);
    NSString *kindText = [defaults stringForKey:@"vk"] ?: @"";
    if (kindText.length == 0) return YES;
    int kind = kindText.intValue;
    if (![kindText isEqualToString:@(kind).stringValue] || kind < 0 || kind > VARIANT_KIND_MINI) return NO;
    Variant *v = malloc(sizeof(Variant));
    variant_init(v, kind, arc4random());
    if (kind == VARIANT_KIND_KILLER && !readCages(&v->shape, [defaults stringForKey:@"vc"] ?: @"")) {
        free(v);
        return NO;
    }
    useVariant(v);
    return YES;
}

static void saveGame(void) {
    char *encoded = game_encode(&game, nowMs());
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:[NSString stringWithUTF8String:encoded] forKey:@"g"];
    [defaults setObject:dailyDate forKey:@"dd"];
    [defaults setObject:variant ? @(variant->shape.kind).stringValue : @"" forKey:@"vk"];
    [defaults setObject:variant && variant->shape.kind == VARIANT_KIND_KILLER ? cageText(&variant->shape) : @"" forKey:@"vc"];
    free(encoded);
}

static NSString *dateKey(NSDate *date) {
    NSDateComponents *c = [[NSCalendar currentCalendar] components:NSCalendarUnitYear | NSCalendarUnitMonth | NSCalendarUnitDay fromDate:date];
    return [NSString stringWithFormat:@"%04ld-%02ld-%02ld", (long)c.year, (long)c.month, (long)c.day];
}

static NSString *todayKey(void) {
    return dateKey([NSDate date]);
}

static NSDate *dateOf(NSString *day) {
    NSDateComponents *c = [NSDateComponents new];
    c.year = [day substringWithRange:NSMakeRange(0, 4)].integerValue;
    c.month = [day substringWithRange:NSMakeRange(5, 2)].integerValue;
    c.day = [day substringWithRange:NSMakeRange(8, 2)].integerValue;
    return [[NSCalendar currentCalendar] dateFromComponents:c];
}

static int dailySeed(NSString *day, int level) {
    int ymd = (int)[day substringWithRange:NSMakeRange(0, 4)].integerValue * 10000 + (int)[day substringWithRange:NSMakeRange(5, 2)].integerValue * 100 + (int)[day substringWithRange:NSMakeRange(8, 2)].integerValue;
    return ymd * 8 + level + 1;
}

static void loadStats(int *a) {
    NSArray *parts = [([[NSUserDefaults standardUserDefaults] stringForKey:@"st"] ?: @"") componentsSeparatedByString:@","];
    for (int i = 0; i < SUDOKU_LEVELS * 4; i++) a[i] = i < (int)parts.count ? MAX(0, [parts[i] intValue]) : 0;
}

static void storeStats(const int *a) {
    NSMutableArray *parts = [NSMutableArray array];
    for (int i = 0; i < SUDOKU_LEVELS * 4; i++) [parts addObject:@(a[i]).stringValue];
    [[NSUserDefaults standardUserDefaults] setObject:[parts componentsJoinedByString:@","] forKey:@"st"];
}

static void recordStart(int level) {
    int a[SUDOKU_LEVELS * 4];
    loadStats(a);
    a[level * 4]++;
    storeStats(a);
}

static NSMutableArray *dailyLog(void) {
    NSMutableArray *days = [NSMutableArray array];
    for (NSString *day in [([[NSUserDefaults standardUserDefaults] stringForKey:@"dl"] ?: @"") componentsSeparatedByString:@";"]) if (day.length == 10) [days addObject:day];
    return days;
}

static void logDaily(NSString *day) {
    NSMutableArray *days = dailyLog();
    if ([days containsObject:day]) return;
    [days addObject:day];
    [days sortUsingSelector:@selector(compare:)];
    while (days.count > 400) [days removeObjectAtIndex:0];
    [[NSUserDefaults standardUserDefaults] setObject:[days componentsJoinedByString:@";"] forKey:@"dl"];
}

static void recordSolved(int level, int seconds, NSString *day) {
    int a[SUDOKU_LEVELS * 4];
    loadStats(a);
    a[level * 4 + 1]++;
    a[level * 4 + 2] += seconds;
    if (a[level * 4 + 3] == 0 || seconds < a[level * 4 + 3]) a[level * 4 + 3] = seconds;
    storeStats(a);
    if (day.length) logDaily(day);
}

static int dailyStreak(void) {
    NSArray *days = dailyLog();
    NSCalendar *calendar = [NSCalendar currentCalendar];
    NSDate *date = [NSDate date];
    if (![days containsObject:dateKey(date)]) date = [calendar dateByAddingUnit:NSCalendarUnitDay value:-1 toDate:date options:0];
    int n = 0;
    while ([days containsObject:dateKey(date)]) {
        n++;
        date = [calendar dateByAddingUnit:NSCalendarUnitDay value:-1 toDate:date options:0];
    }
    return n;
}

static NSString *digitString(const int *values) {
    char buf[82];
    for (int i = 0; i < 81; i++) buf[i] = (char)('0' + values[i]);
    buf[81] = 0;
    return [NSString stringWithUTF8String:buf];
}

static UIColor *rgb(uint32_t argb) {
    return [UIColor colorWithRed:((argb >> 16) & 255) / 255.0 green:((argb >> 8) & 255) / 255.0 blue:(argb & 255) / 255.0 alpha:(argb >> 24) / 255.0];
}

static UIFont *fontOf(CGFloat size, int style) {
    if (style == 2) return [UIFont monospacedDigitSystemFontOfSize:size weight:UIFontWeightRegular];
    return [UIFont systemFontOfSize:size weight:style == 1 ? UIFontWeightBold : UIFontWeightRegular];
}

static CGFloat textWidth(NSString *s, CGFloat size, int style) {
    return [s sizeWithAttributes:@{NSFontAttributeName: fontOf(size, style)}].width;
}

static CGFloat fit(NSString *s, CGFloat size, CGFloat maxWidth) {
    CGFloat w = textWidth(s, size, 0);
    return w > maxWidth ? size * maxWidth / w : size;
}

static void drawText(NSString *s, CGFloat cx, CGFloat cy, CGFloat size, UIColor *color, int style) {
    NSDictionary *attrs = @{NSFontAttributeName: fontOf(size, style), NSForegroundColorAttributeName: color};
    CGSize sz = [s sizeWithAttributes:attrs];
    [s drawAtPoint:CGPointMake(cx - sz.width / 2, cy - sz.height / 2) withAttributes:attrs];
}

static void fillRect(CGFloat l, CGFloat t, CGFloat r, CGFloat b, UIColor *color, CGFloat radius) {
    [color setFill];
    CGRect rect = CGRectMake(l, t, r - l, b - t);
    if (radius > 0) [[UIBezierPath bezierPathWithRoundedRect:rect cornerRadius:radius] fill];
    else UIRectFill(rect);
}

static void strokeSetup(CGContextRef c, UIColor *color, CGFloat width) {
    CGContextSetStrokeColorWithColor(c, color.CGColor);
    CGContextSetLineWidth(c, width);
    CGContextSetLineCap(c, kCGLineCapRound);
    CGContextSetLineJoin(c, kCGLineJoinRound);
}

@interface BoardView : UIView {
    NSString *const *text;
    BOOL dark;
    UIColor *cBg, *cLine, *cThick, *cGiven, *cEntered, *cWrong, *cNote, *cUnit, *cSame, *cSelected, *cKey, *cKeyText, *cMuted, *cPanel, *cDim, *cAccent, *cCorner, *cDiag;
    UIColor *palette[7];
    CGFloat insetL, insetT, insetR, insetB;
    BOOL landscape;
    CGFloat boardX, boardY, boardSize, cell;
    CGFloat topX, topY, topW, topH;
    CGFloat msgX, msgY, msgW, msgH;
    CGFloat toolX[TOOLS], toolY, toolW, toolH;
    CGFloat keyX[9], keyY[9], keyW, keyH;
    BOOL menuOpen, wasGenerating, statsOpen, idle, colorMode, variantsOpen;
    int layoutN;
    int64_t lastInput;
    int downTarget;
    NSTimer *timer;
    CGFloat menuX, menuW, menuTop, menuRowH, menuTitleH;
    int menuRows;
    int menuItems[MENU_MAX][2], menuItemCount[MENU_MAX];
    CGFloat statsX, statsW, statsTop, statsRowH;
    int statsLines;
    int cursor, menuCursor;
    NSMutableDictionary *axElements;
    NSString *axLastMessage;
#ifdef SELFTEST
    int demoDigit;
#endif
}
- (BOOL)handlePress:(UIPress *)press;
- (void)activateTarget:(int)t;
- (void)shown;
- (void)hidden;
- (void)focusChanged:(BOOL)focused;
@end

static BoardView *current;

@interface BoardElement : UIAccessibilityElement
@property (nonatomic) int target;
@end

@implementation BoardElement
- (BOOL)accessibilityActivate {
    [(BoardView *)self.accessibilityContainer activateTarget:self.target];
    return YES;
}
@end

@implementation BoardView

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    text = [[[NSLocale preferredLanguages] firstObject] hasPrefix:@"tr"] ? TR : EN;
    downTarget = -1;
    cursor = 40;
    menuOpen = !game.active && pendingLevel < 0;
    wasGenerating = pendingLevel >= 0;
    lastInput = nowMs();
    self.contentMode = UIViewContentModeRedraw;
#if !TARGET_OS_TV
    self.multipleTouchEnabled = NO;
#endif
    [self applyTheme];
#if TARGET_OS_TV
    UISwipeGestureRecognizerDirection directions[] = {UISwipeGestureRecognizerDirectionUp, UISwipeGestureRecognizerDirectionDown, UISwipeGestureRecognizerDirectionLeft, UISwipeGestureRecognizerDirectionRight};
    for (int i = 0; i < 4; i++) {
        UISwipeGestureRecognizer *swipe = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(swipe:)];
        swipe.direction = directions[i];
        [self addGestureRecognizer:swipe];
    }
#endif
    return self;
}

- (void)applyTheme {
    dark = self.traitCollection.userInterfaceStyle == UIUserInterfaceStyleDark;
    if (dark) {
        cBg = rgb(0xFF121212); cLine = rgb(0xFF3A3A3A); cThick = rgb(0xFFB0B0B0); cGiven = rgb(0xFFF0F0F0);
        cEntered = rgb(0xFF8AB4F8); cWrong = rgb(0xFFF28B82); cNote = rgb(0xFFA0A0A0); cUnit = rgb(0xFF1E2430);
        cSame = rgb(0xFF2B3A55); cSelected = rgb(0xFF3B5A8A); cKey = rgb(0xFF1F1F1F); cKeyText = rgb(0xFFF0F0F0);
        cMuted = rgb(0xFF707070); cPanel = rgb(0xFF1F1F1F); cDim = rgb(0x99000000); cAccent = rgb(0xFF8AB4F8);
        cCorner = rgb(0xFFFFAB70); cDiag = rgb(0xFF211D2B);
    } else {
        cBg = rgb(0xFFFFFFFF); cLine = rgb(0xFFD0D0D0); cThick = rgb(0xFF303030); cGiven = rgb(0xFF202124);
        cEntered = rgb(0xFF1A73E8); cWrong = rgb(0xFFD93025); cNote = rgb(0xFF5F6368); cUnit = rgb(0xFFEEF3FC);
        cSame = rgb(0xFFD2E3FC); cSelected = rgb(0xFFAECBFA); cKey = rgb(0xFFF1F3F4); cKeyText = rgb(0xFF202124);
        cMuted = rgb(0xFF9AA0A6); cPanel = rgb(0xFFFFFFFF); cDim = rgb(0x66000000); cAccent = rgb(0xFF1A73E8);
        cCorner = rgb(0xFFC2410C); cDiag = rgb(0xFFF3F0FB);
    }
    for (int k = 1; k <= GAME_COLORS; k++) palette[k] = rgb(dark ? PALETTE_DARK[k] : PALETTE_LIGHT[k]);
    self.backgroundColor = cBg;
}

- (void)traitCollectionDidChange:(UITraitCollection *)previous {
    [super traitCollectionDidChange:previous];
    [self applyTheme];
    [self setNeedsDisplay];
}

- (void)safeAreaInsetsDidChange {
    [self setNeedsLayout];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    UIEdgeInsets safe = self.safeAreaInsets;
    insetL = safe.left;
    insetT = safe.top;
    insetR = safe.right;
    insetB = safe.bottom;
    [self layoutWidth:self.bounds.size.width height:self.bounds.size.height];
    [self setNeedsDisplay];
}

- (void)syncLayout {
    if (layoutN != game.n) [self layoutWidth:self.bounds.size.width height:self.bounds.size.height];
    if (cursor < 100 && cursor >= game.size) cursor = game.size / 2;
    if (cursor >= 100 + game.n && cursor < 109) cursor = 99 + game.n;
}

- (void)layoutWidth:(CGFloat)w height:(CGFloat)h {
    int n = game.n;
    layoutN = n;
    CGFloat left = insetL;
    CGFloat top = insetT;
    CGFloat cw = w - insetL - insetR;
    CGFloat ch = h - insetT - insetB;
    if (cw <= 0 || ch <= 0) return;
    CGFloat pad = MIN(cw, ch) * 0.03;
    landscape = cw > ch;
    if (landscape) {
        boardSize = MIN(ch - 2 * pad, cw * 0.55);
        boardX = left + pad;
        boardY = top + (ch - boardSize) / 2;
        CGFloat px = boardX + boardSize + pad * 2;
        CGFloat pw = left + cw - pad - px;
        topX = px; topW = pw; topY = boardY; topH = boardSize * 0.11;
        msgX = px; msgW = pw; msgY = topY + topH + pad; msgH = boardSize * 0.11;
        toolW = pw / TOOLS; toolH = boardSize * 0.2; toolY = msgY + msgH + pad;
        for (int i = 0; i < TOOLS; i++) toolX[i] = px + i * toolW;
        CGFloat ky = toolY + toolH + pad;
        keyW = pw / 3;
        keyH = (boardY + boardSize - ky) / (n / 3);
        for (int i = 0; i < n; i++) {
            keyX[i] = px + (i % 3) * keyW;
            keyY[i] = ky + (i / 3) * keyH;
        }
    } else {
        topH = cw * 0.10; msgH = cw * 0.09; toolH = cw * 0.17; keyH = cw * 0.15;
        CGFloat fixed = topH + msgH + toolH + keyH;
        boardSize = MIN(cw - 2 * pad, ch - 2 * pad - fixed - 4 * pad);
        CGFloat gap = MIN((ch - 2 * pad - fixed - boardSize) / 4, pad * 3);
        if (gap < 0) gap = 0;
        CGFloat y = top + pad + (ch - 2 * pad - fixed - boardSize - 4 * gap) / 2;
        boardX = left + (cw - boardSize) / 2;
        topX = boardX; topW = boardSize; topY = y;
        boardY = topY + topH + gap;
        msgX = boardX; msgW = boardSize; msgY = boardY + boardSize + gap;
        toolY = msgY + msgH + gap;
        toolW = boardSize / TOOLS;
        for (int i = 0; i < TOOLS; i++) toolX[i] = boardX + i * toolW;
        keyW = boardSize / n;
        for (int i = 0; i < n; i++) {
            keyX[i] = boardX + i * keyW;
            keyY[i] = toolY + toolH + gap;
        }
    }
    cell = boardSize / n;
}

- (BOOL)overlay {
    return menuOpen || (game.active && game.solved);
}

- (BOOL)cancellable {
    return game.active && !game.solved;
}

- (void)drawRect:(CGRect)rect {
    [cBg setFill];
    UIRectFill(self.bounds);
    [self syncLayout];
    BOOL generating = pendingLevel >= 0;
    if (wasGenerating && !generating) {
        wasGenerating = NO;
        lastInput = nowMs();
        [self resumeIfAllowed];
    }
    [self drawTop:generating];
    if (generating) {
        drawText(text[S_PREPARING], boardX + boardSize / 2, boardY + boardSize / 2, cell * 0.55, cMuted, 0);
    } else if (game.active) {
        [self drawBoard];
    }
    [self drawMessage];
    [self drawTools];
    [self drawKeys];
#if TARGET_OS_TV
    if (!generating && ![self overlay]) [self drawRing:[self targetRect:cursor]];
#endif
    if ([self overlay]) {
        if (statsOpen) [self drawStats];
        else [self drawMenu];
    }
}

- (void)drawBoard {
    int n = game.n;
    const VariantShape *shape = variant ? &variant->shape : NULL;
    BOOL diagonal = shape && shape->kind == VARIANT_KIND_DIAGONAL;
    int sel = game.selected;
    int selValue = sel >= 0 && game.value[sel] != 0 ? game.value[sel] : game.sticky;
    int selBit = selValue == 0 ? 0 : sudoku_bit(selValue);
    for (int i = 0; i < game.size; i++) {
        int row = i / n, col = i % n;
        UIColor *color = nil;
        if (i == sel) color = cSelected;
        else if (game.color[i] != 0) color = palette[game.color[i]];
        else if (selValue != 0 && (game.value[i] == selValue || (game.value[i] == 0 && ((game.notes[i] | game.corner[i]) & selBit) != 0))) color = cSame;
        else if (sel >= 0 && game_sees(&game, sel, i)) color = cUnit;
        else if (diagonal && (row == col || row + col == n - 1)) color = cDiag;
        if (!color) continue;
        CGFloat x = boardX + col * cell;
        CGFloat y = boardY + row * cell;
        fillRect(x, y, x + cell, y + cell, color, 0);
    }
    int noteRows = n / 3;
    BOOL killer = shape && shape->kind == VARIANT_KIND_KILLER;
    CGFloat noteL = killer ? cell * 0.1 : 0, noteT = killer ? cell * 0.27 : 0;
    CGFloat noteW = killer ? cell * 0.8 : cell, noteH = killer ? cell * 0.65 : cell;
    CGFloat noteSize = killer ? cell * 0.2 : cell * 0.28;
    for (int i = 0; i < game.size; i++) {
        CGFloat x0 = boardX + (i % n) * cell;
        CGFloat y0 = boardY + (i / n) * cell;
        int v = game.value[i];
        if (v != 0) {
            BOOL given = game.given[i] != 0;
            UIColor *color = game_conflict(&game, i) || game_wrong(&game, i) ? cWrong : given ? cGiven : cEntered;
            drawText(DIGITS[v], x0 + cell / 2, y0 + cell / 2, cell * 0.62, color, given ? 1 : 0);
        } else if ((game.notes[i] | game.corner[i]) != 0) {
            for (int d = 1; d <= n; d++) {
                int b = sudoku_bit(d);
                if (((game.notes[i] | game.corner[i]) & b) == 0) continue;
                CGFloat nx = x0 + noteL + ((d - 1) % 3 + 0.5) * noteW / 3;
                CGFloat ny = y0 + noteT + ((d - 1) / 3 + 0.5) * noteH / noteRows;
                BOOL same = d == selValue;
                BOOL corner = (game.corner[i] & b) != 0;
                drawText(DIGITS[d], nx, ny, noteSize, same ? cEntered : corner ? cCorner : cNote, same || corner ? 1 : 0);
            }
        }
    }
    if (killer) [self drawCages:shape];
    int boxH = shape ? shape->boxH : 3, boxW = shape ? shape->boxW : 3;
    CGContextRef c = UIGraphicsGetCurrentContext();
    CGContextSetLineCap(c, kCGLineCapButt);
    for (int pass = 0; pass < 2; pass++) {
        BOOL thick = pass == 1;
        CGContextSetStrokeColorWithColor(c, (thick ? cThick : cLine).CGColor);
        CGContextSetLineWidth(c, cell * (thick ? 0.06 : 0.02) * 9 / n);
        for (int k = 0; k <= n; k++) {
            CGFloat p = k * cell;
            if ((k % boxH == 0) == thick) {
                CGContextMoveToPoint(c, boardX, boardY + p);
                CGContextAddLineToPoint(c, boardX + boardSize, boardY + p);
            }
            if ((k % boxW == 0) == thick) {
                CGContextMoveToPoint(c, boardX + p, boardY);
                CGContextAddLineToPoint(c, boardX + p, boardY + boardSize);
            }
        }
        CGContextStrokePath(c);
    }
}

- (void)drawCages:(const VariantShape *)shape {
    int n = shape->n;
    CGFloat inset = cell * 0.07;
    CGContextRef c = UIGraphicsGetCurrentContext();
    CGContextSaveGState(c);
    CGContextSetLineCap(c, kCGLineCapButt);
    CGContextSetStrokeColorWithColor(c, cNote.CGColor);
    CGContextSetLineWidth(c, MAX(1, cell * 0.035));
    CGFloat dash[2] = {cell * 0.09, cell * 0.06};
    CGContextSetLineDash(c, 0, dash, 2);
    for (int i = 0; i < shape->size; i++) {
        int k = shape->cageOf[i];
        if (k < 0) continue;
        int row = i / n, col = i % n;
        CGFloat l = boardX + col * cell + inset, r = boardX + (col + 1) * cell - inset;
        CGFloat t = boardY + row * cell + inset, b = boardY + (row + 1) * cell - inset;
        if (row == 0 || shape->cageOf[i - n] != k) { CGContextMoveToPoint(c, l, t); CGContextAddLineToPoint(c, r, t); }
        if (row == n - 1 || shape->cageOf[i + n] != k) { CGContextMoveToPoint(c, l, b); CGContextAddLineToPoint(c, r, b); }
        if (col == 0 || shape->cageOf[i - 1] != k) { CGContextMoveToPoint(c, l, t); CGContextAddLineToPoint(c, l, b); }
        if (col == n - 1 || shape->cageOf[i + 1] != k) { CGContextMoveToPoint(c, r, t); CGContextAddLineToPoint(c, r, b); }
    }
    CGContextStrokePath(c);
    CGContextRestoreGState(c);
    CGFloat size = cell * 0.2;
    for (int i = 0; i < shape->size; i++) {
        if (!variant_cage_head(shape, i)) continue;
        NSString *sum = @(shape->cageSum[shape->cageOf[i]]).stringValue;
        CGFloat w = textWidth(sum, size, 1);
        CGFloat x = boardX + (i % n) * cell + inset * 0.5;
        CGFloat y = boardY + (i / n) * cell + inset * 0.5;
        UIColor *back = i == game.selected ? cSelected : game.color[i] != 0 ? palette[game.color[i]] : cBg;
        fillRect(x, y, x + w + size * 0.3, y + size * 1.15, back, 0);
        drawText(sum, x + size * 0.15 + w / 2, y + size * 0.58, size, cGiven, 1);
    }
}

- (void)drawKeys {
    CGFloat inset = keyW * 0.06;
    for (int d = 1; d <= game.n; d++) {
        CGFloat x = keyX[d - 1];
        CGFloat y = keyY[d - 1];
        if (colorMode) {
            BOOL usable = d <= 7 && game.selected >= 0;
            fillRect(x + inset, y + inset, x + keyW - inset, y + keyH - inset, d <= 6 ? palette[d] : d == 7 ? cKey : cBg, keyW * 0.15);
            if (d <= 7) drawText(d <= 6 ? DIGITS[d] : @"\u00d7", x + keyW / 2, y + keyH / 2, keyH * 0.42, usable ? cKeyText : cMuted, 0);
            continue;
        }
        BOOL on = d == game.sticky;
        fillRect(x + inset, y + inset, x + keyW - inset, y + keyH - inset, on ? cAccent : cKey, keyW * 0.15);
        int left = game.active ? MAX(0, game_remaining(&game, d)) : game.n;
        drawText(DIGITS[d], x + keyW / 2, y + keyH * 0.42, keyH * 0.5, on ? cBg : left > 0 ? cKeyText : cMuted, 0);
        if (left > 0) drawText(DIGITS[left], x + keyW / 2, y + keyH * 0.8, keyH * 0.2, on ? cBg : cMuted, 0);
    }
}

- (int)targetAt:(CGPoint)p {
    [self syncLayout];
    int n = game.n;
    CGFloat x = p.x, y = p.y;
    if (x >= boardX && x < boardX + boardSize && y >= boardY && y < boardY + boardSize) {
        return MIN(n - 1, (int)((y - boardY) / cell)) * n + MIN(n - 1, (int)((x - boardX) / cell));
    }
    for (int i = 0; i < n; i++) {
        if (x >= keyX[i] && x < keyX[i] + keyW && y >= keyY[i] && y < keyY[i] + keyH) return 100 + i;
    }
    for (int i = 0; i < TOOLS; i++) {
        if (x >= toolX[i] && x < toolX[i] + toolW && y >= toolY && y < toolY + toolH) return 200 + i;
    }
    if (x >= topX && x < topX + topW && y >= topY && y < topY + topH) return 300;
    if ([self explainable] && x >= msgX && x < msgX + msgW && y >= msgY && y < msgY + msgH) return 302;
    return -1;
}

- (BOOL)explainable {
#if TARGET_OS_TV
    return NO;
#else
    return game.active && !game.solved && game_hint_active(&game) && game.hintKind == HINT_PLACE && !variant;
#endif
}

- (NSString *)langPath {
    return text == TR ? @"tr/" : @"";
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (touches.anyObject.type == UITouchTypeIndirect) return;
    [self downAt:[touches.anyObject locationInView:self]];
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (touches.anyObject.type == UITouchTypeIndirect) return;
    [self moveAt:[touches.anyObject locationInView:self]];
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (touches.anyObject.type == UITouchTypeIndirect) return;
    [self upAt:[touches.anyObject locationInView:self]];
}

- (void)downAt:(CGPoint)p {
    [self input];
    if ([self overlay]) {
        downTarget = statsOpen ? [self statsTargetAt:p] : [self menuTargetAt:p];
        return;
    }
    if (pendingLevel >= 0) return;
    int t = [self targetAt:p];
    downTarget = t;
    if (t >= 0 && t < 100) [self finish:game_tap(&game, t)];
}

- (void)moveAt:(CGPoint)p {
    if ([self overlay] || pendingLevel >= 0) return;
    int t = [self targetAt:p];
    if (t >= 0 && t < 100 && t != downTarget) {
        downTarget = -1;
        if (game.active && game.selected != t) {
            game_select(&game, t);
            [self setNeedsDisplay];
        }
    }
}

- (void)upAt:(CGPoint)p {
    if ([self overlay]) {
        int t = statsOpen ? [self statsTargetAt:p] : [self menuTargetAt:p];
        if (t == downTarget) {
            if (statsOpen) [self closeStats];
            else [self menuAction:downTarget];
        }
        downTarget = -1;
        return;
    }
    int t = [self targetAt:p];
    if (t >= 100 && t == downTarget) [self act:t];
    downTarget = -1;
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    downTarget = -1;
}

- (void)act:(int)t {
    if (t >= 100 && t < 109 && t - 99 > game.n) return;
    BOOL changed = NO;
    if (t >= 100 && t < 109 && colorMode) changed = t - 99 <= 7 && game_paint(&game, t - 99 <= 6 ? t - 99 : 0);
    else if (t >= 100 && t < 109) changed = game_key(&game, t - 99);
    else if (t == 200) changed = game_undo(&game);
    else if (t == 201) changed = colorMode ? game_paint(&game, 0) : game_erase(&game);
    else if (t == 202) {
        game_cycle_notes(&game);
        changed = YES;
    } else if (t == 205) {
        colorMode = !colorMode;
        game.sticky = 0;
        changed = YES;
    }
    else if (t == 203) changed = game_fill_notes(&game);
    else if (t == 204) changed = variant ? game_hint_variant(&game, variant) : game_hint(&game, &engine);
    else if (t == 300) {
        [self openMenu];
        return;
    } else if (t == 302) {
#if !TARGET_OS_TV
        NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@%@solver/?p=%@", SITE, [self langPath], digitString(game.value)]];
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
#endif
        return;
    }
    [self finish:changed];
}

- (void)finish:(BOOL)changed {
    if (game.solved) {
        int64_t now = nowMs();
        game_pause(&game, now);
        [self stopTimer];
        if (!recorded) {
            recorded = YES;
            if (!variant) recordSolved(game.level, (int)(game_time(&game, now) / 1000), dailyDate);
        }
    }
    if (changed) saveGame();
    [self setNeedsDisplay];
    NSString *message = game.active && game_hint_active(&game) ? [self hintMessage] : nil;
    if (message && ![message isEqualToString:axLastMessage]) UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, message);
    axLastMessage = message;
    if (game.solved) UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)shown {
    lastInput = nowMs();
    [self resumeIfAllowed];
}

- (void)hidden {
    game_pause(&game, nowMs());
    [self stopTimer];
    [self setNeedsDisplay];
}

- (void)focusChanged:(BOOL)focused {
    if (focused) {
        [self resumeIfAllowed];
        return;
    }
    game_pause(&game, nowMs());
    [self stopTimer];
    saveGame();
    [self setNeedsDisplay];
}

- (BOOL)focused {
    UIWindow *window = self.window;
    return window && window.isKeyWindow && window.windowScene.activationState == UISceneActivationStateForegroundActive;
}

- (void)input {
    lastInput = nowMs();
    if (idle) {
        idle = NO;
        [self resumeIfAllowed];
    }
}

- (void)resumeIfAllowed {
    if (![self overlay] && !idle && pendingLevel < 0 && [self focused]) game_resume(&game, nowMs());
    [self run];
    [self setNeedsDisplay];
}

- (void)stopTimer {
    [timer invalidate];
    timer = nil;
}

- (void)run {
    [self stopTimer];
    if (!game.running) return;
    int64_t now = nowMs();
    if (now - lastInput >= IDLE_MS) {
        idle = YES;
        game_pause(&game, now);
        saveGame();
        [self setNeedsDisplay];
        return;
    }
    [self setNeedsDisplay];
    timer = [NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(run) userInfo:nil repeats:NO];
}

- (NSString *)hintMessage {
    if (game.hintKind == HINT_WRONG) return text[S_WRONG];
    int u = game.hintUnit;
    NSString *s = [text[u == 0 ? S_ROW : u == 1 ? S_COL : u == 2 ? S_BOX : u == 4 ? S_DIAG : u == 5 ? S_REVEAL : S_NAKED] stringByReplacingOccurrencesOfString:@"#" withString:DIGITS[game.hintDigit]];
    int t = game.hintTech;
    if (t > 0) s = [NSString stringWithFormat:@"%@ (%@)", s, text[t < 7 ? S_TECH + t - 1 : S_TECH_EXTRA + t - 7]];
    return s;
}

- (void)drawMessage {
    if (!game.active || !game_hint_active(&game)) return;
    NSString *first = [self hintMessage];
    CGFloat cx = msgX + msgW / 2;
    CGFloat size = msgH * 0.36;
    if (game.hintKind != HINT_PLACE) {
        drawText(first, cx, msgY + msgH / 2, fit(first, size, msgW * 0.96), cWrong, 0);
        return;
    }
    NSString *second = text[S_AGAIN];
    drawText(first, cx, msgY + msgH * 0.3, fit(first, size, msgW * 0.96), cAccent, 0);
    if (![self explainable]) {
        drawText(second, cx, msgY + msgH * 0.72, fit(second, size * 0.9, msgW * 0.96), cMuted, 0);
        return;
    }
    NSString *link = [NSString stringWithFormat:@"  %@ \u203a", text[S_EXPLAIN]];
    CGFloat small = fit([second stringByAppendingString:link], size * 0.9, msgW * 0.96);
    CGFloat w1 = textWidth(second, small, 0), w2 = textWidth(link, small, 0);
    CGFloat left = cx - (w1 + w2) / 2;
    drawText(second, left + w1 / 2, msgY + msgH * 0.72, small, cMuted, 0);
    drawText(link, left + w1 + w2 / 2, msgY + msgH * 0.72, small, cAccent, 0);
}

- (void)drawIcon:(int)kind cx:(CGFloat)cx cy:(CGFloat)cy size:(CGFloat)s color:(UIColor *)color {
    CGContextRef c = UIGraphicsGetCurrentContext();
    strokeSetup(c, color, s * 0.18);
    if (kind == 0) {
        CGContextMoveToPoint(c, cx + s * 0.65, cy);
        CGContextAddLineToPoint(c, cx - s * 0.6, cy);
        CGContextMoveToPoint(c, cx - s * 0.1, cy - s * 0.5);
        CGContextAddLineToPoint(c, cx - s * 0.6, cy);
        CGContextAddLineToPoint(c, cx - s * 0.1, cy + s * 0.5);
    } else if (kind == 1) {
        CGContextMoveToPoint(c, cx + s * 0.686, cy - s * 0.234);
        CGContextAddLineToPoint(c, cx + s * 0.234, cy - s * 0.686);
        CGContextAddLineToPoint(c, cx - s * 0.686, cy + s * 0.234);
        CGContextAddLineToPoint(c, cx - s * 0.234, cy + s * 0.686);
        CGContextClosePath(c);
        CGContextMoveToPoint(c, cx + s * 0.134, cy + s * 0.318);
        CGContextAddLineToPoint(c, cx - s * 0.318, cy - s * 0.134);
    } else if (kind == 2) {
        CGContextSetLineWidth(c, s * 0.3);
        CGContextMoveToPoint(c, cx - s * 0.3, cy + s * 0.3);
        CGContextAddLineToPoint(c, cx + s * 0.5, cy - s * 0.5);
        CGContextStrokePath(c);
        CGContextSetLineWidth(c, s * 0.1);
        CGContextMoveToPoint(c, cx - s * 0.3, cy + s * 0.3);
        CGContextAddLineToPoint(c, cx - s * 0.62, cy + s * 0.62);
    } else if (kind == 3) {
        [color setFill];
        for (int k = 0; k < 9; k++) {
            CGFloat r = s * 0.12;
            [[UIBezierPath bezierPathWithOvalInRect:CGRectMake(cx + (k % 3 - 1) * s * 0.5 - r, cy + (k / 3 - 1) * s * 0.5 - r, 2 * r, 2 * r)] fill];
        }
        return;
    } else if (kind == 5) {
        CGContextAddEllipseInRect(c, CGRectMake(cx - s * 0.62, cy - s * 0.62, s * 1.24, s * 1.24));
        CGContextStrokePath(c);
        [color setFill];
        CGFloat dots[3][2] = {{-0.25, -0.18}, {0.08, -0.32}, {0.3, 0.05}};
        for (int k = 0; k < 3; k++) {
            CGFloat r = s * 0.11;
            [[UIBezierPath bezierPathWithOvalInRect:CGRectMake(cx + dots[k][0] * s - r, cy + dots[k][1] * s - r, 2 * r, 2 * r)] fill];
        }
        return;
    } else {
        CGContextAddEllipseInRect(c, CGRectMake(cx - s * 0.45, cy - s * 0.15 - s * 0.45, s * 0.9, s * 0.9));
        CGContextStrokePath(c);
        CGContextMoveToPoint(c, cx - s * 0.22, cy + s * 0.48);
        CGContextAddLineToPoint(c, cx + s * 0.22, cy + s * 0.48);
        CGContextMoveToPoint(c, cx - s * 0.15, cy + s * 0.7);
        CGContextAddLineToPoint(c, cx + s * 0.15, cy + s * 0.7);
    }
    CGContextStrokePath(c);
}

- (NSString *)toolLabel:(int)i {
    if (i == 5) return text[S_COLOR];
    if (i == 2 && game.noteMode && game.cornerMode) return text[S_CORNER];
    return text[S_UNDO + i];
}

- (void)drawTools {
    CGFloat inset = toolW * 0.06;
    BOOL playable = game.active && !game.solved;
    if (!playable) colorMode = NO;
    for (int i = 0; i < TOOLS; i++) {
        CGFloat x = toolX[i];
        BOOL on = (i == 2 && game.noteMode) || (i == 4 && game.active && game_hint_active(&game) && game.hintKind == HINT_PLACE) || (i == 5 && colorMode);
        BOOL enabled = playable && (i != 0 || game.histCount > 0);
        fillRect(x + inset, toolY + inset, x + toolW - inset, toolY + toolH - inset, on ? cSame : cKey, toolW * 0.15);
        UIColor *color = !enabled ? cMuted : i == 2 && game.cornerMode ? cCorner : on ? cAccent : cKeyText;
        [self drawIcon:i cx:x + toolW / 2 cy:toolY + toolH * 0.38 size:toolH * 0.3 color:color];
        NSString *label = [self toolLabel:i];
        drawText(label, x + toolW / 2, toolY + toolH * 0.78, fit(label, toolH * 0.17, toolW * 0.88), color, 0);
    }
}

- (void)drawTop:(BOOL)generating {
    int level = generating ? pendingLevel : game.active ? game.level : -1;
    CGFloat size = topH * 0.42;
    CGFloat cy = topY + topH / 2;
    if (level >= 0) {
        NSString *s = generating ? [self levelName:level] : [self levelText];
        drawText(s, topX + topH * 0.2 + textWidth(s, size, 1) / 2, cy, size, cKeyText, 1);
    }
    if (game.active && !generating && showTimer) {
        BOOL paused = !game.running && !game.solved && ![self overlay];
        drawText([self clockString:game_time(&game, nowMs())], topX + topW / 2, cy, size, paused ? [cMuted colorWithAlphaComponent:0.4] : cMuted, 2);
    }
    CGFloat mx = topX + topW - topH * 0.45;
    CGContextRef c = UIGraphicsGetCurrentContext();
    strokeSetup(c, cKeyText, topH * 0.07);
    for (int k = -1; k <= 1; k++) {
        CGContextMoveToPoint(c, mx - topH * 0.22, cy + k * topH * 0.17);
        CGContextAddLineToPoint(c, mx + topH * 0.22, cy + k * topH * 0.17);
    }
    CGContextStrokePath(c);
}

- (BOOL)canShare {
#if TARGET_OS_TV
    return NO;
#else
    return YES;
#endif
}

- (void)buildMenu {
    int n = 0;
    if (variantsOpen) {
        for (int k = 0; k < 4; k++) { menuItems[n][0] = 441 + k; menuItemCount[n++] = 1; }
        menuRows = n;
        return;
    }
    BOOL solved = game.active && game.solved;
    BOOL variants = menuKind >= 0;
    int levels = variants ? VARIANT_LEVELS : SUDOKU_LEVELS;
    if (solved && [self canShare]) { menuItems[n][0] = 412; menuItemCount[n++] = 1; }
    for (int level = 0; level < levels; level++) { menuItems[n][0] = 400 + level; menuItemCount[n++] = 1; }
    menuItems[n][0] = 405; menuItems[n][1] = 408; menuItemCount[n++] = 2;
    if (variants) { menuItems[n][0] = 409; menuItems[n][1] = 413; menuItemCount[n++] = 2; }
    else {
        menuItems[n][0] = 409; menuItems[n][1] = 411; menuItemCount[n++] = 2;
        menuItems[n][0] = 413; menuItemCount[n++] = 1;
    }
    if ([self cancellable]) {
        menuItems[n][0] = 406; menuItemCount[n++] = 1;
        menuItems[n][0] = 407; menuItemCount[n++] = 1;
    }
    menuRows = n;
}

- (int)titleLines {
    if (variantsOpen) return 1;
    BOOL solved = game.active && game.solved;
    BOOL dated = solved ? dailyDate.length > 0 : dailyMenu;
    return 2 + (solved ? 1 : 0) + (dated ? 1 : 0);
}

- (void)layoutMenu {
    CGFloat w = self.bounds.size.width, h = self.bounds.size.height;
    CGFloat base = MIN(w - insetL - insetR, h - insetT - insetB);
    menuW = base * 0.82;
    [self buildMenu];
    CGFloat titleRows = 0.4 + [self titleLines] * 0.6;
    menuRowH = MIN(base * 0.095, (h - insetT - insetB) * 0.94 / (titleRows + menuRows + 0.4));
    menuTitleH = menuRowH * titleRows;
    CGFloat total = menuTitleH + menuRows * menuRowH + menuRowH * 0.4;
    menuX = insetL + (w - insetL - insetR - menuW) / 2;
    menuTop = insetT + (h - insetT - insetB - total) / 2;
}

- (CGFloat)menuRowY:(int)row {
    return menuTop + menuTitleH + row * menuRowH;
}

- (CGRect)menuItemRect:(int)row item:(int)item {
    int n = menuItemCount[row];
    CGFloat side = menuRowH * 0.4, gap = menuRowH * 0.12;
    CGFloat w = (menuW - 2 * side - gap * (n - 1)) / n;
    return CGRectMake(menuX + side + item * (w + gap), [self menuRowY:row], w, menuRowH);
}

- (CGRect)menuRectOf:(int)target {
    for (int row = 0; row < menuRows; row++) {
        for (int item = 0; item < menuItemCount[row]; item++) if (menuItems[row][item] == target) return [self menuItemRect:row item:item];
    }
    return CGRectZero;
}

- (int)menuFlatCount {
    int n = 0;
    for (int row = 0; row < menuRows; row++) n += menuItemCount[row];
    return n;
}

- (int)menuFlatTarget:(int)index {
    for (int row = 0; row < menuRows; row++) {
        if (index < menuItemCount[row]) return menuItems[row][index];
        index -= menuItemCount[row];
    }
    return -1;
}

- (NSString *)dateText:(NSString *)day {
    return [NSDateFormatter localizedStringFromDate:dateOf(day) dateStyle:NSDateFormatterLongStyle timeStyle:NSDateFormatterNoStyle];
}

- (NSString *)gameName {
    return variant ? text[S_VARIANT_NAME + variant->shape.kind] : @"";
}

- (NSArray *)titleTexts {
    if (variantsOpen) return @[text[S_VARIANTS]];
    BOOL solved = game.active && game.solved;
    NSMutableArray *lines = [NSMutableArray array];
    [lines addObject:solved ? text[S_SOLVED] : dailyMenu ? text[S_DAILY] : menuKind >= 0 ? text[S_VARIANT_NAME + menuKind] : text[S_TITLE]];
    if (solved) [lines addObject:[NSString stringWithFormat:@"%@%@  %@", variant ? [[self gameName] stringByAppendingString:@" \u00b7 "] : @"", [self levelText], [self clockString:game_time(&game, 0)]]];
    if (solved ? dailyDate.length > 0 : dailyMenu) [lines addObject:[self dateText:solved ? dailyDate : todayKey()]];
    [lines addObject:text[S_NEW]];
    return lines;
}

- (void)drawMenu {
    [cDim setFill];
    UIRectFillUsingBlendMode(self.bounds, kCGBlendModeNormal);
    [self layoutMenu];
    CGFloat bottom = [self menuRowY:menuRows] + menuRowH * 0.4;
    fillRect(menuX, menuTop, menuX + menuW, bottom, cPanel, menuRowH * 0.3);
    BOOL solved = game.active && game.solved;
    CGFloat cx = menuX + menuW / 2;
    NSArray *lines = [self titleTexts];
    CGFloat y = menuTop + menuRowH * 0.7;
    drawText(lines[0], cx, y, fit(lines[0], menuRowH * 0.5, menuW * 0.9), cKeyText, 1);
    for (NSUInteger k = 1; k < lines.count; k++) {
        y += menuRowH * 0.6;
        BOOL result = solved && k == 1;
        drawText(lines[k], cx, y, fit(lines[k], menuRowH * (result ? 0.38 : 0.34), menuW * 0.9), result ? cAccent : cMuted, 0);
    }
    CGFloat inset = menuRowH * 0.08;
    for (int row = 0; row < menuRows; row++) {
        for (int item = 0; item < menuItemCount[row]; item++) {
            CGRect r = [self menuItemRect:row item:item];
            NSString *label = [self menuLabel:menuItems[row][item]];
            fillRect(r.origin.x, r.origin.y + inset, CGRectGetMaxX(r), CGRectGetMaxY(r) - inset, cKey, menuRowH * 0.25);
            drawText(label, CGRectGetMidX(r), CGRectGetMidY(r), fit(label, menuRowH * 0.4, r.size.width * 0.9), cKeyText, 0);
        }
    }
#if TARGET_OS_TV
    menuCursor = MAX(0, MIN([self menuFlatCount] - 1, menuCursor));
    [self drawRing:CGRectInset([self menuRectOf:[self menuFlatTarget:menuCursor]], 0, inset)];
#endif
}

- (int)menuTargetAt:(CGPoint)p {
    [self layoutMenu];
    CGFloat bottom = [self menuRowY:menuRows] + menuRowH * 0.4;
    if (p.x < menuX || p.x > menuX + menuW || p.y < menuTop || p.y > bottom) return 499;
    for (int row = 0; row < menuRows; row++) {
        for (int item = 0; item < menuItemCount[row]; item++) if (CGRectContainsPoint([self menuItemRect:row item:item], p)) return menuItems[row][item];
    }
    return -1;
}

- (void)menuAction:(int)t {
    if (variantsOpen) {
        if (t >= 441 && t <= 443) {
            menuKind = VARIANT_ORDER[t - 441];
            dailyMenu = NO;
        }
        if ((t >= 441 && t <= 444) || t == 499) {
            variantsOpen = NO;
            menuCursor = 0;
            [self setNeedsDisplay];
            UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
        }
        return;
    }
    if (t >= 400 && t < 405) {
        [self startGame:t - 400];
    } else if (t == 405) {
        game.showErrors = !game.showErrors;
        saveGame();
        [self setNeedsDisplay];
    } else if (t == 408) {
        showTimer = !showTimer;
        [[NSUserDefaults standardUserDefaults] setObject:showTimer ? @"1" : @"0" forKey:@"t"];
        [self setNeedsDisplay];
    } else if (t == 409) {
        if (menuKind >= 0) menuKind = -1;
        else dailyMenu = !dailyMenu;
        [self setNeedsDisplay];
        UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    } else if (t == 413) {
        variantsOpen = YES;
        menuCursor = 0;
        [self setNeedsDisplay];
        UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    } else if (t == 411) {
        statsOpen = YES;
        [self setNeedsDisplay];
        UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    } else if (t == 412 && game.active && game.solved) {
        [self share];
    } else if (t == 406 && [self cancellable]) {
        game_restart(&game);
        saveGame();
        [self closeMenu];
    } else if ((t == 407 || t == 499) && [self cancellable]) {
        [self closeMenu];
    }
}

- (NSString *)shareText {
    NSString *result = [NSString stringWithFormat:@"%@ \u00b7 %@", [self levelText], [self clockString:game_time(&game, 0)]];
    if (variant) return [NSString stringWithFormat:@"%@ \u00b7 %@\n%@%@%@", [self gameName], result, SITE, [self langPath], VARIANT_PATHS[variant->shape.kind]];
    if (dailyDate.length) return [NSString stringWithFormat:@"%@ %@ \u00b7 %@\n%@%@daily/", text[S_DAILY], [self dateText:dailyDate], result, SITE, [self langPath]];
    return [NSString stringWithFormat:@"%@ \u00b7 %@\n%@%@?p=%@", text[S_TITLE], result, SITE, [self langPath], digitString(game.given)];
}

- (void)share {
#if !TARGET_OS_TV
    UIViewController *root = self.window.rootViewController;
    if (!root || root.presentedViewController) return;
    UIActivityViewController *sheet = [[UIActivityViewController alloc] initWithActivityItems:@[[self shareText]] applicationActivities:nil];
    sheet.popoverPresentationController.sourceView = self;
    sheet.popoverPresentationController.sourceRect = [self menuRectOf:412];
    [root presentViewController:sheet animated:YES completion:nil];
#endif
}

- (void)layoutStats {
    CGFloat w = self.bounds.size.width, h = self.bounds.size.height;
    CGFloat base = MIN(w - insetL - insetR, h - insetT - insetB);
    statsW = base * 0.9;
    int a[SUDOKU_LEVELS * 4];
    loadStats(a);
    statsLines = 0;
    for (int level = 0; level < SUDOKU_LEVELS; level++) if (a[level * 4] > 0 || a[level * 4 + 1] > 0) statsLines++;
    CGFloat rows = 1.4 + 1 + statsLines + 1.2 + 1.2;
    statsRowH = MIN(base * 0.085, (h - insetT - insetB) * 0.94 / rows);
    statsX = insetL + (w - insetL - insetR - statsW) / 2;
    statsTop = insetT + (h - insetT - insetB - statsRowH * rows) / 2;
}

- (CGRect)statsCloseRect {
    CGFloat y = statsTop + statsRowH * (1.4 + 1 + statsLines + 1.2);
    CGFloat side = statsRowH * 0.4;
    return CGRectMake(statsX + side, y, statsW - 2 * side, statsRowH);
}

- (NSArray *)statsRow:(int)level stats:(const int *)a {
    int p = a[level * 4], s = a[level * 4 + 1], t = a[level * 4 + 2], b = a[level * 4 + 3];
    return @[[self levelName:level], [NSString stringWithFormat:@"%d / %d", s, p], b > 0 ? [self clockString:(int64_t)b * 1000] : @"-", s > 0 ? [self clockString:(int64_t)llround((double)t / s) * 1000] : @"-"];
}

- (void)drawStats {
    [cDim setFill];
    UIRectFillUsingBlendMode(self.bounds, kCGBlendModeNormal);
    [self layoutStats];
    CGRect close = [self statsCloseRect];
    fillRect(statsX, statsTop, statsX + statsW, CGRectGetMaxY(close) + statsRowH * 0.4, cPanel, statsRowH * 0.3);
    CGFloat cx = statsX + statsW / 2;
    drawText(text[S_STATS], cx, statsTop + statsRowH * 0.75, fit(text[S_STATS], statsRowH * 0.55, statsW * 0.9), cKeyText, 1);
    CGFloat colX[4] = {statsX + statsW * 0.2, statsX + statsW * 0.45, statsX + statsW * 0.66, statsX + statsW * 0.86};
    CGFloat colW = statsW * 0.2;
    CGFloat y = statsTop + statsRowH * 1.4 + statsRowH / 2;
    NSString *head[4] = {@"", text[S_PLAYED], text[S_BEST], text[S_AVERAGE]};
    for (int k = 1; k < 4; k++) drawText(head[k], colX[k], y, fit(head[k], statsRowH * 0.3, colW), cMuted, 0);
    int a[SUDOKU_LEVELS * 4];
    loadStats(a);
    for (int level = 0; level < SUDOKU_LEVELS; level++) {
        if (a[level * 4] == 0 && a[level * 4 + 1] == 0) continue;
        y += statsRowH;
        NSArray *cells = [self statsRow:level stats:a];
        for (int k = 0; k < 4; k++) drawText(cells[k], colX[k], y, fit(cells[k], statsRowH * 0.38, k == 0 ? statsW * 0.28 : colW), cKeyText, k == 0 ? 1 : 0);
    }
    NSString *streak = [NSString stringWithFormat:@"%@: %d", text[S_STREAK], dailyStreak()];
    drawText(streak, cx, y + statsRowH * 1.1, fit(streak, statsRowH * 0.38, statsW * 0.9), cMuted, 0);
    fillRect(close.origin.x, close.origin.y + statsRowH * 0.08, CGRectGetMaxX(close), CGRectGetMaxY(close) - statsRowH * 0.08, cKey, statsRowH * 0.25);
    drawText(text[S_CANCEL], CGRectGetMidX(close), CGRectGetMidY(close), fit(text[S_CANCEL], statsRowH * 0.4, close.size.width * 0.9), cKeyText, 0);
#if TARGET_OS_TV
    [self drawRing:close];
#endif
}

- (int)statsTargetAt:(CGPoint)p {
    [self layoutStats];
    CGRect close = [self statsCloseRect];
    if (CGRectContainsPoint(close, p)) return 431;
    if (p.x < statsX || p.x > statsX + statsW || p.y < statsTop || p.y > CGRectGetMaxY(close) + statsRowH * 0.4) return 431;
    return 430;
}

- (NSString *)statsSummary {
    NSMutableString *sb = [NSMutableString stringWithString:text[S_STATS]];
    int a[SUDOKU_LEVELS * 4];
    loadStats(a);
    for (int level = 0; level < SUDOKU_LEVELS; level++) {
        if (a[level * 4] == 0 && a[level * 4 + 1] == 0) continue;
        NSArray *cells = [self statsRow:level stats:a];
        [sb appendFormat:@". %@: %@ %@, %@ %@, %@ %@", cells[0], text[S_PLAYED], cells[1], text[S_BEST], cells[2], text[S_AVERAGE], cells[3]];
    }
    [sb appendFormat:@". %@: %d", text[S_STREAK], dailyStreak()];
    return sb;
}

- (void)closeStats {
    statsOpen = NO;
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)startGame:(int)level {
    if (pendingLevel >= 0) return;
    menuOpen = NO;
    statsOpen = NO;
    idle = NO;
    int kind = menuKind;
    NSString *day = dailyMenu && kind < 0 ? todayKey() : @"";
    game_pause(&game, nowMs());
    [self stopTimer];
    wasGenerating = YES;
    pendingLevel = level;
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        int *puzzle = calloc(81, sizeof(int));
        Variant *shaped = NULL;
        Sudoku *worker = NULL;
        if (kind >= 0) {
            shaped = malloc(sizeof(Variant));
            variant_init(shaped, kind, arc4random());
            variant_generate(shaped, level, puzzle);
        } else {
            worker = malloc(sizeof(Sudoku));
            sudoku_init(worker, ((uint64_t)arc4random() << 32) ^ arc4random());
            if (day.length) sudoku_seed(worker, dailySeed(day, level));
            sudoku_generate(worker, level, puzzle);
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            useVariant(shaped);
            game_start(&game, puzzle, shaped ? shaped->solution : worker->solution, level);
            game.rating = shaped ? 0 : worker->rating;
            free(puzzle);
            free(worker);
            dailyDate = day;
            recorded = NO;
            if (!shaped) recordStart(level);
            saveGame();
            pendingLevel = -1;
            [current setNeedsDisplay];
            UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
        });
    });
}

- (void)openMenu {
    menuOpen = YES;
    variantsOpen = NO;
    menuCursor = 0;
    game_pause(&game, nowMs());
    [self stopTimer];
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)closeMenu {
    menuOpen = NO;
    statsOpen = NO;
    variantsOpen = NO;
    idle = NO;
    lastInput = nowMs();
    [self resumeIfAllowed];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)moveSelection:(int)dir {
    if (!game.active || game.solved) return;
    int n = game.n;
    int i = game.selected < 0 ? 0 : game.selected;
    int r = i / n, c = i % n;
    if (dir == 0) r = (r + n - 1) % n;
    else if (dir == 1) r = (r + 1) % n;
    else if (dir == 2) c = (c + n - 1) % n;
    else c = (c + 1) % n;
    game_select(&game, r * n + c);
    [self setNeedsDisplay];
}

- (CGRect)targetRect:(int)t {
    if (t < 100) return CGRectMake(boardX + (t % game.n) * cell, boardY + (t / game.n) * cell, cell, cell);
    if (t < 109) return CGRectMake(keyX[t - 100], keyY[t - 100], keyW, keyH);
    if (t < 200 + TOOLS) return CGRectMake(toolX[t - 200], toolY, toolW, toolH);
    return CGRectMake(topX + topW - topH * 0.9, topY, topH * 0.9, topH);
}

- (int)neighborOf:(int)from dir:(int)dir {
    CGRect a = [self targetRect:from];
    CGFloat ax = CGRectGetMidX(a), ay = CGRectGetMidY(a);
    int best = from;
    CGFloat bestScore = 1e9;
    for (int t = 0; t <= 300; t++) {
        if (t == from || (t >= game.size && t < 100) || (t >= 100 + game.n && t < 200) || (t >= 200 + TOOLS && t < 300)) continue;
        CGRect b = [self targetRect:t];
        CGFloat dx = CGRectGetMidX(b) - ax, dy = CGRectGetMidY(b) - ay;
        CGFloat primary = dir == 0 ? -dy : dir == 1 ? dy : dir == 2 ? -dx : dx;
        CGFloat secondary = fabs(dir < 2 ? dx : dy);
        if (primary < cell * 0.4) continue;
        CGFloat score = primary + secondary * 2;
        if (score < bestScore) {
            bestScore = score;
            best = t;
        }
    }
    return best;
}

- (void)drawRing:(CGRect)r {
    CGContextRef c = UIGraphicsGetCurrentContext();
    CGFloat w = cell * 0.08;
    strokeSetup(c, cAccent, w);
    CGContextAddPath(c, [UIBezierPath bezierPathWithRoundedRect:CGRectInset(r, w * 0.6, w * 0.6) cornerRadius:w * 1.5].CGPath);
    CGContextStrokePath(c);
}

- (void)moveCursor:(int)dir {
    if (pendingLevel >= 0) return;
    if ([self overlay]) {
        if (statsOpen) return;
        [self layoutMenu];
        menuCursor = MAX(0, MIN([self menuFlatCount] - 1, menuCursor + (dir == 0 || dir == 2 ? -1 : 1)));
    } else {
        cursor = [self neighborOf:cursor dir:dir];
    }
    [self setNeedsDisplay];
}

- (void)swipe:(UISwipeGestureRecognizer *)g {
    UISwipeGestureRecognizerDirection d = g.direction;
    [self moveCursor:d == UISwipeGestureRecognizerDirectionUp ? 0 : d == UISwipeGestureRecognizerDirectionDown ? 1 : d == UISwipeGestureRecognizerDirectionLeft ? 2 : 3];
}

- (BOOL)handlePress:(UIPress *)press {
    int dir = -1, digit = 0, tool = 0;
    BOOL select = NO, menu = NO, play = NO, escape = NO;
    UIKey *key = press.key;
    if (key) {
        UIKeyboardHIDUsage u = key.keyCode;
        if (u >= UIKeyboardHIDUsageKeyboard1 && u <= UIKeyboardHIDUsageKeyboard9) digit = (int)(u - UIKeyboardHIDUsageKeyboard1 + 1);
        else if (u >= UIKeyboardHIDUsageKeypad1 && u <= UIKeyboardHIDUsageKeypad9) digit = (int)(u - UIKeyboardHIDUsageKeypad1 + 1);
        else if (u == UIKeyboardHIDUsageKeyboardUpArrow) dir = 0;
        else if (u == UIKeyboardHIDUsageKeyboardDownArrow) dir = 1;
        else if (u == UIKeyboardHIDUsageKeyboardLeftArrow) dir = 2;
        else if (u == UIKeyboardHIDUsageKeyboardRightArrow) dir = 3;
        else if (u == UIKeyboardHIDUsageKeyboardDeleteOrBackspace || u == UIKeyboardHIDUsageKeyboardDeleteForward) tool = 201;
        else if (u == UIKeyboardHIDUsageKeyboardU) tool = 200;
        else if (u == UIKeyboardHIDUsageKeyboardN) tool = 202;
        else if (u == UIKeyboardHIDUsageKeyboardF) tool = 203;
        else if (u == UIKeyboardHIDUsageKeyboardH) tool = 204;
        else if (u == UIKeyboardHIDUsageKeyboardC) tool = 205;
        else if (u == UIKeyboardHIDUsageKeyboardEscape) escape = YES;
        else if (u == UIKeyboardHIDUsageKeyboardReturnOrEnter || u == UIKeyboardHIDUsageKeyboardSpacebar) select = YES;
        else return NO;
    } else {
        switch (press.type) {
            case UIPressTypeUpArrow: dir = 0; break;
            case UIPressTypeDownArrow: dir = 1; break;
            case UIPressTypeLeftArrow: dir = 2; break;
            case UIPressTypeRightArrow: dir = 3; break;
            case UIPressTypeSelect: select = YES; break;
            case UIPressTypeMenu: menu = YES; break;
            case UIPressTypePlayPause: play = YES; break;
            default: return NO;
        }
    }
    if (pendingLevel >= 0) return !menu;
    [self input];
    if ([self overlay]) {
        if (statsOpen) {
            if (select || menu || escape || play) {
                [self closeStats];
                return YES;
            }
            return dir >= 0;
        }
        if (dir >= 0) {
            [self moveCursor:dir];
            return YES;
        }
        if (select) {
            [self layoutMenu];
            [self menuAction:[self menuFlatTarget:menuCursor]];
            return YES;
        }
        if ((menu || escape) && variantsOpen) {
            [self menuAction:444];
            return YES;
        }
        if ((menu || escape || play) && [self cancellable]) {
            [self closeMenu];
            return YES;
        }
        return !menu;
    }
    if (digit) {
        [self act:99 + digit];
        return YES;
    }
    if (tool) {
        [self act:tool];
        return YES;
    }
    if (escape) {
        if (colorMode) [self act:205];
        else if (game.sticky) [self act:99 + game.sticky];
        else [self openMenu];
        return YES;
    }
    if (play) {
        [self openMenu];
        return YES;
    }
#if TARGET_OS_TV
    if (dir >= 0) {
        [self moveCursor:dir];
        return YES;
    }
    if (select) {
        if (cursor < 100) [self finish:game_tap(&game, cursor)];
        else [self act:cursor];
        return YES;
    }
#else
    if (dir >= 0) {
        [self moveSelection:dir];
        return YES;
    }
#endif
    return NO;
}

- (NSString *)clockString:(int64_t)ms {
    int64_t sec = ms / 1000, min = sec / 60;
    sec %= 60;
    int64_t hour = min / 60;
    min %= 60;
    return hour > 0 ? [NSString stringWithFormat:@"%lld:%02lld:%02lld", hour, min, sec] : [NSString stringWithFormat:@"%lld:%02lld", min, sec];
}

- (NSString *)levelName:(int)level {
    return text[level < 4 ? level : S_MASTER];
}

- (NSString *)levelText {
    NSString *name = [self levelName:game.level];
    return game.rating > 0 ? [NSString stringWithFormat:@"%@ %@", name, [NSString localizedStringWithFormat:@"%.1f", game.rating / 10.0]] : name;
}

- (NSString *)menuLabel:(int)target {
    if (target < 405) return [self levelName:target - 400];
    if (target == 405) return [NSString stringWithFormat:@"%@: %@", text[S_ERRORS], text[game.showErrors ? S_ON : S_OFF]];
    if (target == 408) return [NSString stringWithFormat:@"%@: %@", text[S_TIMER], text[showTimer ? S_ON : S_OFF]];
    if (target == 409) return text[dailyMenu || menuKind >= 0 ? S_PLAY : S_DAILY];
    if (target == 413) return text[S_VARIANTS];
    if (target >= 441 && target <= 443) return text[S_VARIANT_NAME + VARIANT_ORDER[target - 441]];
    if (target == 411) return text[S_STATS];
    if (target == 412) return text[S_SHARE];
    return text[target == 406 ? S_RESTART : S_CANCEL];
}

- (BOOL)isAccessibilityElement {
    return NO;
}

- (BoardElement *)axElement:(int)key frame:(CGRect)frame label:(NSString *)label value:(NSString *)value traits:(UIAccessibilityTraits)traits {
    if (!axElements) axElements = [NSMutableDictionary dictionary];
    BoardElement *e = axElements[@(key)];
    if (!e) {
        e = [[BoardElement alloc] initWithAccessibilityContainer:self];
        e.target = key;
        axElements[@(key)] = e;
    }
    e.accessibilityFrameInContainerSpace = frame;
    e.accessibilityLabel = label;
    e.accessibilityValue = value;
    e.accessibilityTraits = traits;
    return e;
}

- (NSString *)cellValue:(int)i {
    int v = game.value[i];
    if (v != 0) {
        BOOL wrong = game_conflict(&game, i) || game_wrong(&game, i);
        NSString *digit = wrong ? [NSString stringWithFormat:@"%@, %@", DIGITS[v], text[S_WRONG]] : DIGITS[v];
        if (game.color[i]) digit = [NSString stringWithFormat:@"%@, %@ %d", digit, text[S_COLOR], game.color[i]];
        NSString *cage = [self cageLabel:i];
        return cage ? [NSString stringWithFormat:@"%@, %@", digit, cage] : digit;
    }
    NSMutableArray *parts = [NSMutableArray array];
    if (game.notes[i] != 0) {
        NSMutableString *notes = [NSMutableString stringWithString:text[S_NOTE]];
        for (int d = 1; d <= 9; d++) if (game.notes[i] & sudoku_bit(d)) [notes appendFormat:@" %d", d];
        [parts addObject:notes];
    }
    if (game.corner[i] != 0) {
        NSMutableString *corner = [NSMutableString stringWithString:text[S_CORNER]];
        for (int d = 1; d <= 9; d++) if (game.corner[i] & sudoku_bit(d)) [corner appendFormat:@" %d", d];
        [parts addObject:corner];
    }
    if (game.color[i] != 0) [parts addObject:[NSString stringWithFormat:@"%@ %d", text[S_COLOR], game.color[i]]];
    NSString *cage = [self cageLabel:i];
    if (cage) [parts addObject:cage];
    return parts.count ? [parts componentsJoinedByString:@", "] : nil;
}

- (NSString *)cageLabel:(int)i {
    if (!variant || !variant_cage_head(&variant->shape, i)) return nil;
    return [text[S_CAGE] stringByReplacingOccurrencesOfString:@"#" withString:@(variant->shape.cageSum[variant->shape.cageOf[i]]).stringValue];
}

- (NSArray *)accessibilityElements {
    NSMutableArray *list = [NSMutableArray array];
    if (pendingLevel >= 0) {
        [list addObject:[self axElement:420 frame:self.bounds label:text[S_PREPARING] value:nil traits:UIAccessibilityTraitStaticText]];
        return list;
    }
    if ([self overlay]) {
        if (statsOpen) {
            [self layoutStats];
            CGRect close = [self statsCloseRect];
            [list addObject:[self axElement:430 frame:CGRectMake(statsX, statsTop, statsW, close.origin.y - statsTop) label:[self statsSummary] value:nil traits:UIAccessibilityTraitStaticText]];
            [list addObject:[self axElement:431 frame:close label:text[S_CANCEL] value:nil traits:UIAccessibilityTraitButton]];
            return list;
        }
        [self layoutMenu];
        [list addObject:[self axElement:410 frame:CGRectMake(menuX, menuTop, menuW, menuTitleH) label:[[self titleTexts] componentsJoinedByString:@". "] value:nil traits:UIAccessibilityTraitHeader]];
        for (int row = 0; row < menuRows; row++) {
            for (int item = 0; item < menuItemCount[row]; item++) {
                int target = menuItems[row][item];
                [list addObject:[self axElement:target frame:[self menuItemRect:row item:item] label:[self menuLabel:target] value:nil traits:UIAccessibilityTraitButton]];
            }
        }
        return list;
    }
    [self syncLayout];
    BOOL playable = game.active && !game.solved;
    NSString *level = variant ? [NSString stringWithFormat:@"%@, %@", [self gameName], [self levelText]] : [self levelText];
    NSString *top = game.active ? (showTimer ? [NSString stringWithFormat:@"%@, %@", level, [self clockString:game_time(&game, nowMs())]] : level) : @"";
    [list addObject:[self axElement:301 frame:CGRectMake(topX, topY, topW - topH * 0.9, topH) label:top value:nil traits:UIAccessibilityTraitStaticText | UIAccessibilityTraitUpdatesFrequently]];
    [list addObject:[self axElement:300 frame:[self targetRect:300] label:text[S_NEW] value:nil traits:UIAccessibilityTraitButton]];
    if (game.active && game_hint_active(&game)) {
        NSString *message = [self hintMessage];
        if (game.hintKind == HINT_PLACE) message = [NSString stringWithFormat:@"%@. %@", message, text[S_AGAIN]];
        if ([self explainable]) message = [NSString stringWithFormat:@"%@. %@", message, text[S_EXPLAIN]];
        [list addObject:[self axElement:302 frame:CGRectMake(msgX, msgY, msgW, msgH) label:message value:nil traits:[self explainable] ? UIAccessibilityTraitButton : UIAccessibilityTraitStaticText]];
    }
    for (int i = 0; i < game.size; i++) {
        NSString *label = [NSString stringWithFormat:@"%@ %d, %@ %d", text[S_ROWLABEL], i / game.n + 1, text[S_COLLABEL], i % game.n + 1];
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (i == game.selected) traits |= UIAccessibilityTraitSelected;
        if (!playable) traits |= UIAccessibilityTraitNotEnabled;
        [list addObject:[self axElement:i frame:[self targetRect:i] label:label value:game.active ? [self cellValue:i] : nil traits:traits]];
    }
    for (int d = 1; d <= game.n; d++) {
        if (colorMode) {
            UIAccessibilityTraits traits = UIAccessibilityTraitButton;
            if (!playable || d > 7 || game.selected < 0) traits |= UIAccessibilityTraitNotEnabled;
            NSString *label = d <= 6 ? [NSString stringWithFormat:@"%@ %d", text[S_COLOR], d] : d == 7 ? text[S_ERASE] : @"";
            [list addObject:[self axElement:99 + d frame:[self targetRect:99 + d] label:label value:nil traits:traits]];
            continue;
        }
        int left = game.active ? MAX(0, game_remaining(&game, d)) : game.n;
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (d == game.sticky) traits |= UIAccessibilityTraitSelected;
        if (!playable || left == 0) traits |= UIAccessibilityTraitNotEnabled;
        NSString *value = [text[S_LEFT] stringByReplacingOccurrencesOfString:@"#" withString:[NSString stringWithFormat:@"%d", left]];
        [list addObject:[self axElement:99 + d frame:[self targetRect:99 + d] label:DIGITS[d] value:value traits:traits]];
    }
    for (int i = 0; i < TOOLS; i++) {
        BOOL on = (i == 2 && game.noteMode) || (i == 4 && game.active && game_hint_active(&game) && game.hintKind == HINT_PLACE) || (i == 5 && colorMode);
        BOOL enabled = playable && (i != 0 || game.histCount > 0);
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (on) traits |= UIAccessibilityTraitSelected;
        if (!enabled) traits |= UIAccessibilityTraitNotEnabled;
        [list addObject:[self axElement:200 + i frame:[self targetRect:200 + i] label:[self toolLabel:i] value:nil traits:traits]];
    }
    return list;
}

- (void)activateTarget:(int)t {
    if (pendingLevel >= 0 || t < 0) return;
    [self input];
    if ([self overlay]) {
        if (statsOpen) {
            if (t == 431) [self closeStats];
        } else if (t >= 400 && t != 410 && t != 420) [self menuAction:t];
        return;
    }
    if (t < 100) [self finish:game_tap(&game, t)];
    else if (t == 302) {
        if ([self explainable]) [self act:t];
    } else if (t >= 100) [self act:t];
}

- (CGPoint)menuPointOf:(int)target {
    [self layoutMenu];
    CGRect r = [self menuRectOf:target];
    return CGPointMake(CGRectGetMidX(r), CGRectGetMidY(r));
}

#ifdef SELFTEST
- (void)axDump:(int)step {
    if (!getenv("BARESUDOKU_AXDUMP")) return;
    for (BoardElement *e in [self accessibilityElements]) {
        CGRect f = e.accessibilityFrameInContainerSpace;
        fprintf(stderr, "ax %d: %d [%s] [%s] traits=%llu frame=%.0f,%.0f %.0fx%.0f\n", step, e.target, e.accessibilityLabel.UTF8String, e.accessibilityValue.UTF8String ?: "", (unsigned long long)e.accessibilityTraits, f.origin.x, f.origin.y, f.size.width, f.size.height);
    }
}

- (void)press:(CGPoint)p {
    [self downAt:p];
    [self upAt:p];
}

- (CGPoint)cellPoint:(int)i {
    return CGPointMake(boardX + (i % game.n + 0.5) * cell, boardY + (i / game.n + 0.5) * cell);
}

- (void)selftest:(NSNumber *)stepNumber {
    int step = stepNumber.intValue;
    if (step == 0) [self press:[self menuPointOf:400]];
    else if (step == 1) [self press:CGPointMake(keyX[2] + keyW / 2, keyY[2] + keyH / 2)];
    else if (step == 2) [self press:[self cellPoint:0]];
    else if (step == 3) [self press:CGPointMake(toolX[2] + toolW / 2, toolY + toolH / 2)];
    else if (step == 4) [self press:[self cellPoint:1]];
    else if (step == 5) [self press:CGPointMake(toolX[4] + toolW / 2, toolY + toolH / 2)];
    else if (step == 6) [self press:CGPointMake(topX + topW - topH * 0.45, topY + topH / 2)];
    else if (step == 7) [self press:[self menuPointOf:407]];
    NSLog(@"selftest %d: active=%d pending=%d sel=%d sticky=%d note=%d v0=%d given0=%d n1=%d given1=%d hint=%d menu=%d rows=%d", step, game.active, pendingLevel, game.selected, game.sticky, game.noteMode, game.value[0], game.given[0], game.notes[1], game.given[1], game.hintKind, menuOpen, menuRows);
    if (step < 7) [self performSelector:@selector(selftest:) withObject:@(step + 1) afterDelay:1.5];
}

- (void)variants:(NSNumber *)stepNumber {
    int step = stepNumber.intValue;
    if (pendingLevel >= 0) {
        [self performSelector:@selector(variants:) withObject:stepNumber afterDelay:0.5];
        return;
    }
    static const int menuSteps[3][3] = {{441, 401, 0}, {442, 402, 0}, {443, 400, 0}};
    CGPoint hint = CGPointMake(toolX[4] + toolW / 2, toolY + toolH / 2);
    int round = step / 5, phase = step % 5;
    if (round < 3) {
        if (phase == 0) { if (!menuOpen) [self press:CGPointMake(topX + topW - topH * 0.45, topY + topH / 2)]; [self press:[self menuPointOf:413]]; }
        else if (phase == 1) [self press:[self menuPointOf:menuSteps[round][0]]];
        else if (phase == 2) [self press:[self menuPointOf:menuSteps[round][1]]];
        else if (phase == 3) { [self press:hint]; [self snapshot:[NSString stringWithFormat:@"v%d-hint", round]]; [self axDump:step]; }
        else { [self press:hint]; [self press:CGPointMake(toolX[3] + toolW / 2, toolY + toolH / 2)]; [self snapshot:[NSString stringWithFormat:@"v%d-notes", round]]; }
    }
    NSLog(@"variants %d: kind=%d size=%d n=%d active=%d hint=%d menu=%d variantsOpen=%d", step, variant ? variant->shape.kind : -1, game.size, game.n, game.active, game.hintKind, menuOpen, variantsOpen);
    if (step < 14) [self performSelector:@selector(variants:) withObject:@(step + 1) afterDelay:1.0];
}

- (void)snapshot:(NSString *)name {
    const char *dir = getenv("BARESUDOKU_SHOTS");
    if (!dir) return;
    UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat defaultFormat];
    format.scale = self.traitCollection.displayScale;
    format.opaque = YES;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:self.bounds.size format:format];
    NSData *png = [renderer PNGDataWithActions:^(UIGraphicsImageRendererContext *context) { [self drawRect:self.bounds]; }];
    [png writeToFile:[NSString stringWithFormat:@"%s/%@.png", dir, name] atomically:YES];
}

- (void)demo:(NSNumber *)stepNumber {
    int step = stepNumber.intValue;
    int given = 0;
    while (given < 80 && game.given[given] == 0) given++;
    CGPoint hint = CGPointMake(toolX[4] + toolW / 2, toolY + toolH / 2);
    if (step == 0) { self.window.overrideUserInterfaceStyle = UIUserInterfaceStyleLight; [self applyTheme]; [self press:[self menuPointOf:402]]; }
    else if (step <= 12) [self press:hint];
    else if (step == 13) { [self press:[self cellPoint:given]]; [self snapshot:@"1-board"]; }
    else if (step == 14) {
        demoDigit = 1;
        for (int d = 2; d <= 9; d++) if (game_remaining(&game, d) > game_remaining(&game, demoDigit)) demoDigit = d;
        [self press:[self cellPoint:given]];
        [self press:CGPointMake(keyX[demoDigit - 1] + keyW / 2, keyY[demoDigit - 1] + keyH / 2)];
        [self snapshot:@"2-lock"];
    }
    else if (step == 15) { [self press:CGPointMake(keyX[demoDigit - 1] + keyW / 2, keyY[demoDigit - 1] + keyH / 2)]; [self press:hint]; [self snapshot:@"3-hint"]; }
    else if (step == 16) { [self press:CGPointMake(topX + topW - topH * 0.45, topY + topH / 2)]; [self snapshot:@"4-menu"]; }
    else if (step == 17) [self press:[self menuPointOf:407]];
    else if (step == 18) self.window.overrideUserInterfaceStyle = UIUserInterfaceStyleDark;
    else if (step == 19) { [self applyTheme]; [self snapshot:@"5-dark"]; }
    NSLog(@"demo %d: sel=%d sticky=%d hint=%d menu=%d size=%.0fx%.0f", step, game.selected, game.sticky, game.hintKind, menuOpen, self.bounds.size.width, self.bounds.size.height);
    [self axDump:step];
    if (step < 19) [self performSelector:@selector(demo:) withObject:@(step + 1) afterDelay:1.5];
}
#endif

@end

@interface BoardController : UIViewController
@end

@implementation BoardController
- (void)loadView {
    current = [[BoardView alloc] initWithFrame:CGRectZero];
    self.view = current;
}
- (BOOL)canBecomeFirstResponder {
    return YES;
}
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self becomeFirstResponder];
}
- (void)pressesBegan:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
    for (UIPress *press in presses) if ([current handlePress:press]) return;
    [super pressesBegan:presses withEvent:event];
}
@end

@interface SceneDelegate : UIResponder <UIWindowSceneDelegate>
@property (nonatomic, strong) UIWindow *window;
@end

@implementation SceneDelegate
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    self.window.rootViewController = [BoardController new];
    [self.window makeKeyAndVisible];
#if defined(SELFTEST) && TARGET_OS_MACCATALYST
    UIWindowScene *ws = (UIWindowScene *)scene;
    ws.titlebar.titleVisibility = UITitlebarTitleVisibilityHidden;
    ws.titlebar.toolbar = nil;
    ws.sizeRestrictions.minimumSize = CGSizeMake(1280, 800);
    ws.sizeRestrictions.maximumSize = CGSizeMake(1280, 800);
#endif
}
- (void)sceneDidBecomeActive:(UIScene *)scene {
    [current shown];
#ifdef SELFTEST
    static BOOL started;
    if (getenv("BARESUDOKU_SELFTEST") && !started) {
        started = YES;
        const char *mode = getenv("BARESUDOKU_SELFTEST");
        SEL run = strcmp(mode, "demo") == 0 ? @selector(demo:) : strcmp(mode, "variants") == 0 ? @selector(variants:) : @selector(selftest:);
        [current performSelector:run withObject:@0 afterDelay:1.5];
    }
#endif
}
- (void)sceneWillResignActive:(UIScene *)scene {
    [current hidden];
    saveGame();
}
- (void)sceneDidEnterBackground:(UIScene *)scene {
    saveGame();
}
@end

@interface AppDelegate : UIResponder <UIApplicationDelegate>
@end

@implementation AppDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    sudoku_init(&engine, ((uint64_t)arc4random() << 32) ^ arc4random());
    game_init(&game);
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    if (restoreVariant(defaults)) game_decode(&game, [defaults stringForKey:@"g"].UTF8String);
    if (!game.active) useVariant(NULL);
    dailyDate = game.active ? ([defaults stringForKey:@"dd"] ?: @"") : @"";
    dailyMenu = dailyDate.length > 0;
    menuKind = variant ? variant->shape.kind : -1;
    showTimer = ![[defaults stringForKey:@"t"] isEqualToString:@"0"];
    recorded = game.solved;
    if (game.active && !variant) game.rating = sudoku_rate(&engine, game.given);
    [[NSNotificationCenter defaultCenter] addObserverForName:UIWindowDidResignKeyNotification object:nil queue:nil usingBlock:^(NSNotification *note) { [current focusChanged:NO]; }];
    [[NSNotificationCenter defaultCenter] addObserverForName:UIWindowDidBecomeKeyNotification object:nil queue:nil usingBlock:^(NSNotification *note) { [current focusChanged:YES]; }];
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
    }
}
