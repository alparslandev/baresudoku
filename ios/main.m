#import <UIKit/UIKit.h>
#include <string.h>
#include <time.h>
#include "Sudoku.h"
#include "Game.h"

enum { S_UNDO = 4, S_ERASE, S_NOTE, S_FILL, S_HINT, S_NEW, S_ERRORS, S_ON, S_OFF, S_CANCEL, S_SOLVED, S_PREPARING,
    S_WRONG, S_NAKED, S_ROW, S_COL, S_BOX, S_AGAIN, S_TECH, S_TITLE = 28, S_RESTART = 29, S_ROWLABEL = 30, S_COLLABEL = 31, S_LEFT = 32, S_TECH_EXTRA = 33,
    S_MASTER = S_TECH_EXTRA + SUDOKU_TECH_COUNT - 7 };

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
    @"Nested Forcing Net", @"Sashimi X-Wing", @"Sashimi Swordfish", @"Sashimi Jellyfish", @"Master"};
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
    @"Nested Forcing Net", @"Sashimi X-Wing", @"Sashimi Swordfish", @"Sashimi Jellyfish", @"Usta"};
static NSString *const DIGITS[] = {@"", @"1", @"2", @"3", @"4", @"5", @"6", @"7", @"8", @"9"};

static Game game;
static Sudoku engine;
static int pendingLevel = -1;

static int64_t nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void saveGame(void) {
    char *encoded = game_encode(&game, nowMs());
    [[NSUserDefaults standardUserDefaults] setObject:[NSString stringWithUTF8String:encoded] forKey:@"g"];
    free(encoded);
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
    UIColor *cBg, *cLine, *cThick, *cGiven, *cEntered, *cWrong, *cNote, *cUnit, *cSame, *cSelected, *cKey, *cKeyText, *cMuted, *cPanel, *cDim, *cAccent;
    CGFloat insetL, insetT, insetR, insetB;
    BOOL landscape;
    CGFloat boardX, boardY, boardSize, cell;
    CGFloat topX, topY, topW, topH;
    CGFloat msgX, msgY, msgW, msgH;
    CGFloat toolX[5], toolY, toolW, toolH;
    CGFloat keyX[9], keyY[9], keyW, keyH;
    BOOL menuOpen, wasGenerating;
    int downTarget;
    NSTimer *timer;
    CGFloat menuX, menuW, menuTop, menuRowH, menuTitleH;
    int menuRows;
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
    } else {
        cBg = rgb(0xFFFFFFFF); cLine = rgb(0xFFD0D0D0); cThick = rgb(0xFF303030); cGiven = rgb(0xFF202124);
        cEntered = rgb(0xFF1A73E8); cWrong = rgb(0xFFD93025); cNote = rgb(0xFF5F6368); cUnit = rgb(0xFFEEF3FC);
        cSame = rgb(0xFFD2E3FC); cSelected = rgb(0xFFAECBFA); cKey = rgb(0xFFF1F3F4); cKeyText = rgb(0xFF202124);
        cMuted = rgb(0xFF9AA0A6); cPanel = rgb(0xFFFFFFFF); cDim = rgb(0x66000000); cAccent = rgb(0xFF1A73E8);
    }
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

- (void)layoutWidth:(CGFloat)w height:(CGFloat)h {
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
        toolW = pw / 5; toolH = boardSize * 0.2; toolY = msgY + msgH + pad;
        for (int i = 0; i < 5; i++) toolX[i] = px + i * toolW;
        CGFloat ky = toolY + toolH + pad;
        keyW = pw / 3;
        keyH = (boardY + boardSize - ky) / 3;
        for (int i = 0; i < 9; i++) {
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
        toolW = boardSize / 5;
        for (int i = 0; i < 5; i++) toolX[i] = boardX + i * toolW;
        keyW = boardSize / 9;
        for (int i = 0; i < 9; i++) {
            keyX[i] = boardX + i * keyW;
            keyY[i] = toolY + toolH + gap;
        }
    }
    cell = boardSize / 9;
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
    BOOL generating = pendingLevel >= 0;
    if (wasGenerating && !generating) {
        wasGenerating = NO;
        game_resume(&game, nowMs());
        [self run];
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
    if ([self overlay]) [self drawMenu];
}

- (void)drawBoard {
    int sel = game.selected;
    int selValue = sel >= 0 && game.value[sel] != 0 ? game.value[sel] : game.sticky;
    int selBit = selValue == 0 ? 0 : sudoku_bit(selValue);
    for (int i = 0; i < 81; i++) {
        UIColor *color = nil;
        if (i == sel) color = cSelected;
        else if (selValue != 0 && (game.value[i] == selValue || (game.value[i] == 0 && (game.notes[i] & selBit) != 0))) color = cSame;
        else if (sel >= 0 && sudoku_sees(i, sel)) color = cUnit;
        if (!color) continue;
        CGFloat x = boardX + SUDOKU_COL[i] * cell;
        CGFloat y = boardY + SUDOKU_ROW[i] * cell;
        fillRect(x, y, x + cell, y + cell, color, 0);
    }
    for (int i = 0; i < 81; i++) {
        CGFloat cx = boardX + (SUDOKU_COL[i] + 0.5) * cell;
        CGFloat cy = boardY + (SUDOKU_ROW[i] + 0.5) * cell;
        int v = game.value[i];
        if (v != 0) {
            BOOL given = game.given[i] != 0;
            UIColor *color = game_conflict(&game, i) || game_wrong(&game, i) ? cWrong : given ? cGiven : cEntered;
            drawText(DIGITS[v], cx, cy, cell * 0.62, color, given ? 1 : 0);
        } else if (game.notes[i] != 0) {
            CGFloat x0 = boardX + SUDOKU_COL[i] * cell;
            CGFloat y0 = boardY + SUDOKU_ROW[i] * cell;
            for (int d = 1; d <= 9; d++) {
                if ((game.notes[i] & sudoku_bit(d)) == 0) continue;
                CGFloat nx = x0 + ((d - 1) % 3 + 0.5) * cell / 3;
                CGFloat ny = y0 + ((d - 1) / 3 + 0.5) * cell / 3;
                BOOL same = d == selValue;
                drawText(DIGITS[d], nx, ny, cell * 0.28, same ? cEntered : cNote, same ? 1 : 0);
            }
        }
    }
    CGContextRef c = UIGraphicsGetCurrentContext();
    CGContextSetLineCap(c, kCGLineCapButt);
    for (int k = 0; k <= 9; k++) {
        BOOL thick = k % 3 == 0;
        CGContextSetStrokeColorWithColor(c, (thick ? cThick : cLine).CGColor);
        CGContextSetLineWidth(c, cell * (thick ? 0.06 : 0.02));
        CGFloat p = k * cell;
        CGContextMoveToPoint(c, boardX, boardY + p);
        CGContextAddLineToPoint(c, boardX + boardSize, boardY + p);
        CGContextMoveToPoint(c, boardX + p, boardY);
        CGContextAddLineToPoint(c, boardX + p, boardY + boardSize);
        CGContextStrokePath(c);
    }
}

- (void)drawKeys {
    CGFloat inset = keyW * 0.06;
    for (int d = 1; d <= 9; d++) {
        CGFloat x = keyX[d - 1];
        CGFloat y = keyY[d - 1];
        BOOL on = d == game.sticky;
        fillRect(x + inset, y + inset, x + keyW - inset, y + keyH - inset, on ? cAccent : cKey, keyW * 0.15);
        int left = game.active ? MAX(0, game_remaining(&game, d)) : 9;
        drawText(DIGITS[d], x + keyW / 2, y + keyH * 0.42, keyH * 0.5, on ? cBg : left > 0 ? cKeyText : cMuted, 0);
        if (left > 0) drawText(DIGITS[left], x + keyW / 2, y + keyH * 0.8, keyH * 0.2, on ? cBg : cMuted, 0);
    }
}

- (int)targetAt:(CGPoint)p {
    CGFloat x = p.x, y = p.y;
    if (x >= boardX && x < boardX + boardSize && y >= boardY && y < boardY + boardSize) {
        return (int)((y - boardY) / cell) * 9 + (int)((x - boardX) / cell);
    }
    for (int i = 0; i < 9; i++) {
        if (x >= keyX[i] && x < keyX[i] + keyW && y >= keyY[i] && y < keyY[i] + keyH) return 100 + i;
    }
    for (int i = 0; i < 5; i++) {
        if (x >= toolX[i] && x < toolX[i] + toolW && y >= toolY && y < toolY + toolH) return 200 + i;
    }
    if (x >= topX && x < topX + topW && y >= topY && y < topY + topH) return 300;
    return -1;
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
    if ([self overlay]) {
        downTarget = [self menuTargetAt:p];
        return;
    }
    if (pendingLevel >= 0) return;
    int t = [self targetAt:p];
    downTarget = t;
    if (t >= 0 && t < 81) [self finish:game_tap(&game, t)];
}

- (void)moveAt:(CGPoint)p {
    if ([self overlay] || pendingLevel >= 0) return;
    int t = [self targetAt:p];
    if (t >= 0 && t < 81 && t != downTarget) {
        downTarget = -1;
        if (game.active && game.selected != t) {
            game_select(&game, t);
            [self setNeedsDisplay];
        }
    }
}

- (void)upAt:(CGPoint)p {
    if ([self overlay]) {
        if ([self menuTargetAt:p] == downTarget) [self menuAction:downTarget];
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
    BOOL changed = NO;
    if (t >= 100 && t < 109) changed = game_key(&game, t - 99);
    else if (t == 200) changed = game_undo(&game);
    else if (t == 201) changed = game_erase(&game);
    else if (t == 202) game.noteMode = !game.noteMode;
    else if (t == 203) changed = game_fill_notes(&game);
    else if (t == 204) changed = game_hint(&game, &engine);
    else if (t == 300) {
        [self openMenu];
        return;
    }
    [self finish:changed || t == 202];
}

- (void)finish:(BOOL)changed {
    if (game.solved) {
        game_pause(&game, nowMs());
        [self stopTimer];
    }
    if (changed) saveGame();
    [self setNeedsDisplay];
    NSString *message = game.active && game_hint_active(&game) ? [self hintMessage] : nil;
    if (message && ![message isEqualToString:axLastMessage]) UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, message);
    axLastMessage = message;
    if (game.solved) UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)shown {
    game_resume(&game, nowMs());
    [self run];
}

- (void)hidden {
    game_pause(&game, nowMs());
    [self stopTimer];
}

- (void)stopTimer {
    [timer invalidate];
    timer = nil;
}

- (void)run {
    [self stopTimer];
    if (game.running) {
        [self setNeedsDisplay];
        timer = [NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(run) userInfo:nil repeats:NO];
    }
}

- (NSString *)hintMessage {
    if (game.hintKind == HINT_WRONG) return text[S_WRONG];
    int u = game.hintUnit;
    NSString *s = [text[u == 0 ? S_ROW : u == 1 ? S_COL : u == 2 ? S_BOX : S_NAKED] stringByReplacingOccurrencesOfString:@"#" withString:DIGITS[game.hintDigit]];
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
    drawText(second, cx, msgY + msgH * 0.72, fit(second, size * 0.9, msgW * 0.96), cMuted, 0);
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

- (void)drawTools {
    CGFloat inset = toolW * 0.06;
    for (int i = 0; i < 5; i++) {
        CGFloat x = toolX[i];
        BOOL on = (i == 2 && game.noteMode) || (i == 4 && game.active && game_hint_active(&game) && game.hintKind == HINT_PLACE);
        BOOL enabled = game.active && !game.solved && (i != 0 || game.histCount > 0);
        fillRect(x + inset, toolY + inset, x + toolW - inset, toolY + toolH - inset, on ? cSame : cKey, toolW * 0.15);
        UIColor *color = !enabled ? cMuted : on ? cAccent : cKeyText;
        [self drawIcon:i cx:x + toolW / 2 cy:toolY + toolH * 0.38 size:toolH * 0.3 color:color];
        NSString *label = text[S_UNDO + i];
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
    if (game.active && !generating) drawText([self clockString:game_time(&game, nowMs())], topX + topW / 2, cy, size, cMuted, 2);
    CGFloat mx = topX + topW - topH * 0.45;
    CGContextRef c = UIGraphicsGetCurrentContext();
    strokeSetup(c, cKeyText, topH * 0.07);
    for (int k = -1; k <= 1; k++) {
        CGContextMoveToPoint(c, mx - topH * 0.22, cy + k * topH * 0.17);
        CGContextAddLineToPoint(c, mx + topH * 0.22, cy + k * topH * 0.17);
    }
    CGContextStrokePath(c);
}

- (void)layoutMenu {
    CGFloat w = self.bounds.size.width, h = self.bounds.size.height;
    CGFloat base = MIN(w - insetL - insetR, h - insetT - insetB);
    menuW = base * 0.82;
    menuRowH = base * 0.095;
    BOOL solved = game.active && game.solved;
    menuTitleH = menuRowH * (solved ? 2.2 : 1.6);
    menuRows = [self cancellable] ? 8 : 6;
    CGFloat total = menuTitleH + menuRows * menuRowH + menuRowH * 0.4;
    menuX = insetL + (w - insetL - insetR - menuW) / 2;
    menuTop = insetT + (h - insetT - insetB - total) / 2;
}

- (CGFloat)menuRowY:(int)row {
    return menuTop + menuTitleH + row * menuRowH;
}

- (void)drawMenu {
    [cDim setFill];
    UIRectFillUsingBlendMode(self.bounds, kCGBlendModeNormal);
    [self layoutMenu];
    CGFloat bottom = [self menuRowY:menuRows] + menuRowH * 0.4;
    fillRect(menuX, menuTop, menuX + menuW, bottom, cPanel, menuRowH * 0.3);
    BOOL solved = game.active && game.solved;
    CGFloat cx = menuX + menuW / 2;
    CGFloat y = menuTop + menuRowH * 0.7;
    drawText(text[solved ? S_SOLVED : S_TITLE], cx, y, menuRowH * 0.5, cKeyText, 1);
    y += menuRowH * 0.6;
    if (solved) {
        drawText([NSString stringWithFormat:@"%@  %@", [self levelText], [self clockString:game_time(&game, 0)]], cx, y, menuRowH * 0.38, cAccent, 0);
        y += menuRowH * 0.6;
    }
    drawText(text[S_NEW], cx, y, menuRowH * 0.34, cMuted, 0);
    CGFloat side = menuRowH * 0.4;
    CGFloat inset = menuRowH * 0.08;
    for (int row = 0; row < menuRows; row++) {
        CGFloat ry = [self menuRowY:row];
        NSString *label = [self menuLabel:row];
        fillRect(menuX + side, ry + inset, menuX + menuW - side, ry + menuRowH - inset, cKey, menuRowH * 0.25);
        drawText(label, cx, ry + menuRowH / 2, fit(label, menuRowH * 0.4, menuW * 0.8), cKeyText, 0);
    }
#if TARGET_OS_TV
    CGFloat ry = [self menuRowY:menuCursor];
    [self drawRing:CGRectMake(menuX + side, ry + inset, menuW - 2 * side, menuRowH - 2 * inset)];
#endif
}

- (int)menuTargetAt:(CGPoint)p {
    [self layoutMenu];
    CGFloat bottom = [self menuRowY:menuRows] + menuRowH * 0.4;
    if (p.x < menuX || p.x > menuX + menuW || p.y < menuTop || p.y > bottom) return 499;
    for (int row = 0; row < menuRows; row++) {
        CGFloat ry = [self menuRowY:row];
        if (p.y >= ry && p.y < ry + menuRowH) return 400 + row;
    }
    return -1;
}

- (void)menuAction:(int)t {
    if (t >= 400 && t < 405) {
        [self startGame:t - 400];
    } else if (t == 405) {
        game.showErrors = !game.showErrors;
        saveGame();
        [self setNeedsDisplay];
    } else if (t == 406 && [self cancellable]) {
        game_restart(&game);
        saveGame();
        [self closeMenu];
    } else if ((t == 407 || t == 499) && [self cancellable]) {
        [self closeMenu];
    }
}

- (void)startGame:(int)level {
    if (pendingLevel >= 0) return;
    menuOpen = NO;
    game_pause(&game, nowMs());
    [self stopTimer];
    wasGenerating = YES;
    pendingLevel = level;
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        Sudoku *worker = malloc(sizeof(Sudoku));
        int *puzzle = malloc(sizeof(int) * 81);
        sudoku_init(worker, ((uint64_t)arc4random() << 32) ^ arc4random());
        sudoku_generate(worker, level, puzzle);
        dispatch_async(dispatch_get_main_queue(), ^{
            game_start(&game, puzzle, worker->solution, level);
            game.rating = worker->rating;
            free(puzzle);
            free(worker);
            saveGame();
            pendingLevel = -1;
            [current setNeedsDisplay];
            UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
        });
    });
}

- (void)openMenu {
    menuOpen = YES;
    menuCursor = 0;
    game_pause(&game, nowMs());
    [self stopTimer];
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)closeMenu {
    menuOpen = NO;
    game_resume(&game, nowMs());
    [self run];
    [self setNeedsDisplay];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, nil);
}

- (void)moveSelection:(int)dir {
    if (!game.active || game.solved) return;
    int i = game.selected < 0 ? 0 : game.selected;
    int r = SUDOKU_ROW[i], c = SUDOKU_COL[i];
    if (dir == 0) r = (r + 8) % 9;
    else if (dir == 1) r = (r + 1) % 9;
    else if (dir == 2) c = (c + 8) % 9;
    else c = (c + 1) % 9;
    game_select(&game, r * 9 + c);
    [self setNeedsDisplay];
}

- (CGRect)targetRect:(int)t {
    if (t < 81) return CGRectMake(boardX + SUDOKU_COL[t] * cell, boardY + SUDOKU_ROW[t] * cell, cell, cell);
    if (t < 109) return CGRectMake(keyX[t - 100], keyY[t - 100], keyW, keyH);
    if (t < 205) return CGRectMake(toolX[t - 200], toolY, toolW, toolH);
    return CGRectMake(topX + topW - topH * 0.9, topY, topH * 0.9, topH);
}

- (int)neighborOf:(int)from dir:(int)dir {
    CGRect a = [self targetRect:from];
    CGFloat ax = CGRectGetMidX(a), ay = CGRectGetMidY(a);
    int best = from;
    CGFloat bestScore = 1e9;
    for (int t = 0; t <= 300; t++) {
        if (t == from || (t > 80 && t < 100) || (t > 108 && t < 200) || (t > 204 && t < 300)) continue;
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
        if (dir > 1) return;
        [self layoutMenu];
        menuCursor = MAX(0, MIN(menuRows - 1, menuCursor + (dir == 0 ? -1 : 1)));
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
    if ([self overlay]) {
        if (dir == 0 || dir == 1) {
            [self moveCursor:dir];
            return YES;
        }
        if (select) {
            [self layoutMenu];
            [self menuAction:400 + menuCursor];
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
        if (game.sticky) [self act:99 + game.sticky];
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
        if (cursor < 81) [self finish:game_tap(&game, cursor)];
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

- (NSString *)menuLabel:(int)row {
    if (row < 5) return [self levelName:row];
    if (row == 5) return [NSString stringWithFormat:@"%@: %@", text[S_ERRORS], text[game.showErrors ? S_ON : S_OFF]];
    return text[row == 6 ? S_RESTART : S_CANCEL];
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
        return wrong ? [NSString stringWithFormat:@"%@, %@", DIGITS[v], text[S_WRONG]] : DIGITS[v];
    }
    if (game.notes[i] == 0) return nil;
    NSMutableString *notes = [NSMutableString stringWithString:text[S_NOTE]];
    for (int d = 1; d <= 9; d++) if (game.notes[i] & sudoku_bit(d)) [notes appendFormat:@" %d", d];
    return notes;
}

- (NSArray *)accessibilityElements {
    NSMutableArray *list = [NSMutableArray array];
    if (pendingLevel >= 0) {
        [list addObject:[self axElement:420 frame:self.bounds label:text[S_PREPARING] value:nil traits:UIAccessibilityTraitStaticText]];
        return list;
    }
    if ([self overlay]) {
        [self layoutMenu];
        BOOL solved = game.active && game.solved;
        NSString *title = solved ? [NSString stringWithFormat:@"%@ %@ %@", text[S_SOLVED], [self levelText], [self clockString:game_time(&game, 0)]] : text[S_TITLE];
        [list addObject:[self axElement:410 frame:CGRectMake(menuX, menuTop, menuW, menuTitleH) label:[NSString stringWithFormat:@"%@. %@", title, text[S_NEW]] value:nil traits:UIAccessibilityTraitHeader]];
        for (int row = 0; row < menuRows; row++) {
            [list addObject:[self axElement:400 + row frame:CGRectMake(menuX, [self menuRowY:row], menuW, menuRowH) label:[self menuLabel:row] value:nil traits:UIAccessibilityTraitButton]];
        }
        return list;
    }
    BOOL playable = game.active && !game.solved;
    NSString *top = game.active ? [NSString stringWithFormat:@"%@, %@", [self levelText], [self clockString:game_time(&game, nowMs())]] : @"";
    [list addObject:[self axElement:301 frame:CGRectMake(topX, topY, topW - topH * 0.9, topH) label:top value:nil traits:UIAccessibilityTraitStaticText | UIAccessibilityTraitUpdatesFrequently]];
    [list addObject:[self axElement:300 frame:[self targetRect:300] label:text[S_NEW] value:nil traits:UIAccessibilityTraitButton]];
    if (game.active && game_hint_active(&game)) {
        NSString *message = [self hintMessage];
        if (game.hintKind == HINT_PLACE) message = [NSString stringWithFormat:@"%@. %@", message, text[S_AGAIN]];
        [list addObject:[self axElement:302 frame:CGRectMake(msgX, msgY, msgW, msgH) label:message value:nil traits:UIAccessibilityTraitStaticText]];
    }
    for (int i = 0; i < 81; i++) {
        NSString *label = [NSString stringWithFormat:@"%@ %d, %@ %d", text[S_ROWLABEL], SUDOKU_ROW[i] + 1, text[S_COLLABEL], SUDOKU_COL[i] + 1];
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (i == game.selected) traits |= UIAccessibilityTraitSelected;
        if (!playable) traits |= UIAccessibilityTraitNotEnabled;
        [list addObject:[self axElement:i frame:[self targetRect:i] label:label value:game.active ? [self cellValue:i] : nil traits:traits]];
    }
    for (int d = 1; d <= 9; d++) {
        int left = game.active ? MAX(0, game_remaining(&game, d)) : 9;
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (d == game.sticky) traits |= UIAccessibilityTraitSelected;
        if (!playable || left == 0) traits |= UIAccessibilityTraitNotEnabled;
        NSString *value = [text[S_LEFT] stringByReplacingOccurrencesOfString:@"#" withString:[NSString stringWithFormat:@"%d", left]];
        [list addObject:[self axElement:99 + d frame:[self targetRect:99 + d] label:DIGITS[d] value:value traits:traits]];
    }
    for (int i = 0; i < 5; i++) {
        BOOL on = (i == 2 && game.noteMode) || (i == 4 && game.active && game_hint_active(&game) && game.hintKind == HINT_PLACE);
        BOOL enabled = playable && (i != 0 || game.histCount > 0);
        UIAccessibilityTraits traits = UIAccessibilityTraitButton;
        if (on) traits |= UIAccessibilityTraitSelected;
        if (!enabled) traits |= UIAccessibilityTraitNotEnabled;
        [list addObject:[self axElement:200 + i frame:[self targetRect:200 + i] label:text[S_UNDO + i] value:nil traits:traits]];
    }
    return list;
}

- (void)activateTarget:(int)t {
    if (pendingLevel >= 0 || t < 0) return;
    if ([self overlay]) {
        if (t >= 400) [self menuAction:t];
        return;
    }
    if (t < 81) [self finish:game_tap(&game, t)];
    else if (t >= 100) [self act:t];
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
    return CGPointMake(boardX + (SUDOKU_COL[i] + 0.5) * cell, boardY + (SUDOKU_ROW[i] + 0.5) * cell);
}

- (void)selftest:(NSNumber *)stepNumber {
    int step = stepNumber.intValue;
    if (step == 0) { [self layoutMenu]; [self press:CGPointMake(menuX + menuW / 2, [self menuRowY:0] + menuRowH / 2)]; }
    else if (step == 1) [self press:CGPointMake(keyX[2] + keyW / 2, keyY[2] + keyH / 2)];
    else if (step == 2) [self press:[self cellPoint:0]];
    else if (step == 3) [self press:CGPointMake(toolX[2] + toolW / 2, toolY + toolH / 2)];
    else if (step == 4) [self press:[self cellPoint:1]];
    else if (step == 5) [self press:CGPointMake(toolX[4] + toolW / 2, toolY + toolH / 2)];
    else if (step == 6) [self press:CGPointMake(topX + topW - topH * 0.45, topY + topH / 2)];
    else if (step == 7) { [self layoutMenu]; [self press:CGPointMake(menuX + menuW / 2, [self menuRowY:7] + menuRowH / 2)]; }
    NSLog(@"selftest %d: active=%d pending=%d sel=%d sticky=%d note=%d v0=%d given0=%d n1=%d given1=%d hint=%d menu=%d rows=%d", step, game.active, pendingLevel, game.selected, game.sticky, game.noteMode, game.value[0], game.given[0], game.notes[1], game.given[1], game.hintKind, menuOpen, menuRows);
    if (step < 7) [self performSelector:@selector(selftest:) withObject:@(step + 1) afterDelay:1.5];
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
    if (step == 0) { self.window.overrideUserInterfaceStyle = UIUserInterfaceStyleLight; [self applyTheme]; [self layoutMenu]; [self press:CGPointMake(menuX + menuW / 2, [self menuRowY:2] + menuRowH / 2)]; }
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
    else if (step == 17) { [self layoutMenu]; [self press:CGPointMake(menuX + menuW / 2, [self menuRowY:7] + menuRowH / 2)]; }
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
        SEL run = strcmp(getenv("BARESUDOKU_SELFTEST"), "demo") == 0 ? @selector(demo:) : @selector(selftest:);
        [current performSelector:run withObject:@0 afterDelay:1.5];
    }
#endif
}
- (void)sceneWillResignActive:(UIScene *)scene {
    [current hidden];
    saveGame();
}
@end

@interface AppDelegate : UIResponder <UIApplicationDelegate>
@end

@implementation AppDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    sudoku_init(&engine, ((uint64_t)arc4random() << 32) ^ arc4random());
    game_init(&game);
    game_decode(&game, [[NSUserDefaults standardUserDefaults] stringForKey:@"g"].UTF8String);
    if (game.active) game.rating = sudoku_rate(&engine, game.given);
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
    }
}
