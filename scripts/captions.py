import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

STORE = Path(__file__).resolve().parent.parent / "ios" / "store"
FONT_FILE = "/System/Library/Fonts/HelveticaNeue.ttc"
LIGHT = {"paper": (255, 255, 255), "ink": (17, 17, 17), "rim": (214, 217, 222), "shade": 46}
DARK = {"paper": (17, 17, 17), "ink": (255, 255, 255), "rim": (58, 60, 64), "shade": 0}
NO_ADS = ("No ads.", "No account.")
HINTS = ("Hints that", "explain why.")
LOCK = ("Lock a digit,", "fill fast.")
LEVELS = ("Pick your", "level.")
NIGHT = ("Works offline.", "Dark mode too.")
PHONE = {"letter": 0.131, "top": 0.050, "leading": 1.12, "gap": 0.040, "wide": 0.800, "corner": 0.085}
TABLET = {"letter": 0.076, "top": 0.036, "leading": 1.14, "gap": 0.030, "wide": 0.700, "corner": 0.030}
SHOTS = [
    ("1-board", "cap-1-noads", NO_ADS, PHONE, LIGHT),
    ("4-menu", "cap-2-hints", HINTS, PHONE, LIGHT),
    ("2-digit-first", "cap-3-lock", LOCK, PHONE, LIGHT),
    ("3-hint", "cap-4-levels", LEVELS, PHONE, LIGHT),
    ("5-dark", "cap-5-dark", NIGHT, PHONE, DARK),
    ("ipad-1-board", "cap-ipad-1-noads", NO_ADS, TABLET, LIGHT),
    ("ipad-3-hint", "cap-ipad-2-hints", HINTS, TABLET, LIGHT),
    ("ipad-2-lock", "cap-ipad-3-lock", LOCK, TABLET, LIGHT),
    ("ipad-4-menu", "cap-ipad-4-levels", LEVELS, TABLET, LIGHT),
    ("ipad-5-dark", "cap-ipad-5-dark", NIGHT, TABLET, DARK),
]


def bold_face(size):
    for index in range(16):
        try:
            face = ImageFont.truetype(FONT_FILE, size, index=index)
        except OSError:
            break
        if face.getname() == ("Helvetica Neue", "Bold"):
            return face
    raise SystemExit("Helvetica Neue Bold bulunamadi")


def compose(source, target, lines, layout, tones):
    shot = Image.open(STORE / f"{source}.png").convert("RGB")
    width, height = shot.size
    page = Image.new("RGB", (width, height), tones["paper"])
    pen = ImageDraw.Draw(page)
    face = bold_face(round(width * layout["letter"]))
    step = round(face.size * layout["leading"])
    top = round(height * layout["top"])
    for row, text in enumerate(lines):
        pen.text((width // 2, top + row * step), text, font=face, fill=tones["ink"], anchor="ma")
    card_top = top + len(lines) * step + round(height * layout["gap"])
    card_wide = round(width * layout["wide"])
    card_tall = round(card_wide * height / width)
    card_left = (width - card_wide) // 2
    corner = round(card_wide * layout["corner"])
    if tones["shade"]:
        blur = width // 40
        veil = Image.new("L", (width, height), 0)
        ImageDraw.Draw(veil).rounded_rectangle((card_left, card_top + blur // 2, card_left + card_wide, card_top + card_tall + blur // 2), corner, fill=tones["shade"])
        page.paste(tones["ink"], (0, 0), veil.filter(ImageFilter.GaussianBlur(blur)))
    small = shot.resize((card_wide, card_tall), Image.LANCZOS)
    hole = Image.new("L", (card_wide, card_tall), 0)
    ImageDraw.Draw(hole).rounded_rectangle((0, 0, card_wide - 1, card_tall - 1), corner, fill=255)
    page.paste(small, (card_left, card_top), hole)
    pen.rounded_rectangle((card_left, card_top, card_left + card_wide - 1, card_top + card_tall - 1), corner, outline=tones["rim"], width=max(3, width // 330))
    page.save(STORE / f"{target}.png", optimize=True)
    return f"{target}.png"


def main():
    for shot in SHOTS:
        print(compose(*shot))
    return 0


if __name__ == "__main__":
    sys.exit(main())
