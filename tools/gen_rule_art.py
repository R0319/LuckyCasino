"""ルールブックの絵（gen_font.py が luckycasino:icons のフォントに入れる）。

どれも本の文字と同じ行に並べる小さな絵。高さ 9 の絵は 1 行に収まり、高さ 13 の絵（カード・ルーレットの盤）は
下に 1 行あけて使う。文字の割り当ては paper の Glyphs と同じにすること。
"""
from PIL import Image, ImageDraw

from gen_cards import FONT, OUTLINE, plot
from gen_games import SEGMENT_COLOR, SILKS
from gen_table import LETTERS, R_BLACK, R_GREEN, R_RED, REDS

DIE = 0xE010        # サイコロの目 1〜6
BOWL = 0xE016       # どんぶり
WHEEL = 0xE020      # ビッグホイールのマス（BigWheel.Segment の順）
SILK = 0xE030       # 競馬の枠の色と番号 1〜6
ROULETTE = 0xE040   # ルーレットの盤と賭け方（ROULETTE_BETS の順）
BACCARAT = 0xE050   # バカラの賭け場所 P・B・T
HIGHLOW = 0xE058    # ハイ・ロー・同じ
ICON = 0xE060       # ほかの小さな絵（ICONS の順）
CARD_BACK = 0xE140  # カードの裏

CLEAR = (0, 0, 0, 0)
WHITE = (244, 242, 236, 255)
INK = (40, 36, 34, 255)
PIP_RED = (200, 30, 40, 255)
GOLD = (222, 176, 72, 255)
GOLD_DARK = (150, 106, 36, 255)
WOOD = (150, 100, 56, 255)
WOOD_DARK = (104, 66, 34, 255)
CHIP_RED = (190, 40, 44, 255)
FELT = (30, 112, 62, 255)
LIT = (255, 236, 120, 255)
BLUE = (40, 84, 190, 255)
GREEN = (30, 140, 70, 255)
GREY = (120, 116, 112, 255)


def canvas(w, h):
    img = Image.new("RGBA", (w, h), CLEAR)
    return img, ImageDraw.Draw(img)


def rounded(d, box, fill, outline=OUTLINE):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=outline)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=fill)
    for x, y in [(x0, y0), (x1, y0), (x0, y1), (x1, y1)]:
        d.point((x, y), fill=CLEAR)


def label(img, s, x, y, color):
    glyphs = {**FONT, **LETTERS}
    for ch in s:
        g = glyphs[ch]
        plot(img, g, x, y, color)
        x += len(g[0]) + 1


def label_width(s):
    glyphs = {**FONT, **LETTERS}
    return sum(len(glyphs[ch][0]) + 1 for ch in s) - 1


# ---- サイコロ（9 x 9） ----
PIPS = {1: [(4, 4)], 2: [(2, 2), (6, 6)], 3: [(2, 2), (4, 4), (6, 6)], 4: [(2, 2), (6, 2), (2, 6), (6, 6)],
        5: [(2, 2), (6, 2), (4, 4), (2, 6), (6, 6)], 6: [(2, 2), (6, 2), (2, 4), (6, 4), (2, 6), (6, 6)]}


def die(n):
    img, d = canvas(9, 9)
    rounded(d, [0, 0, 8, 8], WHITE)
    if n == 1:
        # 1 の目は大きく赤く
        d.rectangle([3, 3, 5, 5], fill=PIP_RED)
        for x, y in [(3, 3), (5, 3), (3, 5), (5, 5)]:
            d.point((x, y), fill=WHITE)
        d.point((4, 4), fill=PIP_RED)
        d.rectangle([4, 3, 4, 5], fill=PIP_RED)
        d.rectangle([3, 4, 5, 4], fill=PIP_RED)
    else:
        for x, y in PIPS[n]:
            d.point((x, y), fill=INK)
    return img


def bowl():
    img, d = canvas(11, 9)
    d.rectangle([0, 1, 10, 2], fill=(40, 52, 120, 255))
    d.polygon([(0, 3), (10, 3), (8, 7), (2, 7)], fill=WHITE, outline=OUTLINE)
    d.line([(1, 4), (9, 4)], fill=(40, 52, 120, 255))
    d.line([(3, 8), (7, 8)], fill=OUTLINE)
    return img


# ---- ビッグホイールのマス（9 x 9。色は gen_games の SEGMENT_COLOR と同じ） ----
WHEEL_SEGMENTS = [("A", "1"), ("B", "2"), ("E", "5"), ("T", "10"), ("W", "20"), ("J", "J"), ("L", "LC")]


def wheel_tile(seg, text):
    img, d = canvas(9, 9)
    rounded(d, [0, 0, 8, 8], SEGMENT_COLOR[seg])
    color = INK if seg == "A" else WHITE
    if seg == "J":
        # ジョーカーは道化の帽子
        d.polygon([(1, 6), (2, 2), (4, 5), (6, 2), (7, 6)], fill=(200, 40, 60, 255))
        d.point((2, 1), fill=GOLD)
        d.point((6, 1), fill=GOLD)
        d.line([(1, 7), (7, 7)], fill=GOLD)
        return img
    label(img, text, (9 - label_width(text)) // 2, 2, color)
    return img


# ---- 競馬の枠（9 x 9、番号入り） ----
def silk(i):
    img, d = canvas(9, 9)
    color = SILKS[i]
    rounded(d, [0, 0, 8, 8], color)
    text_color = INK if i in (0, 4) else WHITE
    label(img, str(i + 1), (9 - label_width(str(i + 1))) // 2, 2, text_color)
    return img


# ---- ルーレットの盤（53 x 13）。左に 0、その右に 12 列 x 3 段。1 マス 3 x 3 ----
ROULETTE_BETS = ["straight", "split", "street", "corner", "line", "dozen", "column", "red", "zero"]
CELL = 4


def number_at(col, row):
    """col 0〜11、row 0（上）〜2（下）。上の段が 3・6・9…"""
    return col * 3 + (3 - row)


def roulette_board(bet):
    w, h = 1 + CELL + 12 * CELL, 1 + 3 * CELL
    img, d = canvas(w, h)
    d.rectangle([0, 0, w - 1, h - 1], fill=FELT)
    lit = set()
    chips = []
    if bet == "straight":
        lit = {17}
        chips = [(5, 1, "center")]
    elif bet == "split":
        lit = {17, 20}
        chips = [(5, 1, "right")]
    elif bet == "street":
        lit = {16, 17, 18}
        chips = [(5, 2, "bottom")]
    elif bet == "corner":
        lit = {17, 18, 20, 21}
        chips = [(5, 0, "corner")]
    elif bet == "line":
        lit = {16, 17, 18, 19, 20, 21}
        chips = [(5, 2, "bottom_right")]
    elif bet == "dozen":
        lit = set(range(13, 25))
    elif bet == "column":
        lit = {n for n in range(1, 37) if n % 3 == 0}
    elif bet == "red":
        lit = set(REDS)
    elif bet == "zero":
        lit = {0}

    def cell_box(col, row):
        x = 1 + CELL + col * CELL
        y = 1 + row * CELL
        return [x, y, x + CELL - 2, y + CELL - 2]

    # 0（左の縦長のマス）
    zero = [1, 1, CELL - 1, h - 2]
    d.rectangle(zero, fill=LIT if 0 in lit else R_GREEN)
    for col in range(12):
        for row in range(3):
            n = number_at(col, row)
            base = R_RED if n in REDS else R_BLACK
            d.rectangle(cell_box(col, row), fill=LIT if n in lit else base)
            if n in lit and bet != "red":
                # 当たるマスは明るくし、元の色を真ん中に 1 点だけ残す
                x0, y0, _, _ = cell_box(col, row)
                d.point((x0 + 1, y0 + 1), fill=base)
    if bet == "red":
        for col in range(12):
            for row in range(3):
                n = number_at(col, row)
                if n in REDS:
                    x0, y0, _, _ = cell_box(col, row)
                    d.rectangle([x0, y0, x0 + 2, y0 + 2], fill=(236, 70, 70, 255))
    for col, row, where in chips:
        x0, y0, x1, y1 = cell_box(col, row)
        cx, cy = {"center": (x0 + 1, y0 + 1), "right": (x1 + 1, y0 + 1), "bottom": (x0 + 1, y1 + 1),
                  "corner": (x1 + 1, y1 + 1), "bottom_right": (x1 + 1, y1 + 1)}[where]
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=(40, 84, 190, 255))
        d.point((cx, cy), fill=WHITE)
    return img


# ---- バカラの賭け場所（9 x 9 の丸） ----
def baccarat_spot(letter, color):
    img, d = canvas(9, 9)
    d.ellipse([0, 0, 8, 8], fill=OUTLINE)
    d.ellipse([1, 1, 7, 7], fill=color)
    label(img, letter, 3, 2, WHITE)
    return img


# ---- ハイ・ロー・同じ（9 x 9） ----
def highlow(kind):
    img, d = canvas(9, 9)
    colors = {"high": (200, 60, 40, 255), "low": (40, 90, 200, 255), "same": (60, 150, 70, 255)}
    rounded(d, [0, 0, 8, 8], colors[kind])
    if kind == "high":
        d.polygon([(4, 1), (7, 4), (1, 4)], fill=WHITE)
        d.rectangle([3, 5, 5, 7], fill=WHITE)
    elif kind == "low":
        d.polygon([(4, 7), (7, 4), (1, 4)], fill=WHITE)
        d.rectangle([3, 1, 5, 3], fill=WHITE)
    else:
        d.rectangle([2, 2, 6, 3], fill=WHITE)
        d.rectangle([2, 5, 6, 6], fill=WHITE)
    return img


# ---- ほかの小さな絵（9 x 9） ----
def chair():
    img, d = canvas(9, 9)
    d.rectangle([5, 0, 6, 8], fill=WOOD_DARK)
    d.rectangle([1, 4, 6, 5], fill=(160, 30, 40, 255))
    d.rectangle([1, 6, 1, 8], fill=WOOD_DARK)
    d.rectangle([5, 0, 7, 1], fill=WOOD)
    return img


def chip():
    img, d = canvas(9, 9)
    for i, y in enumerate((5, 3, 1)):
        d.ellipse([0, y, 8, y + 3], fill=OUTLINE)
        d.ellipse([1, y, 7, y + 2], fill=CHIP_RED if i != 1 else (40, 84, 190, 255))
        d.point((4, y + 1), fill=WHITE)
    return img


def coin():
    img, d = canvas(9, 9)
    d.ellipse([0, 0, 8, 8], fill=GOLD_DARK)
    d.ellipse([1, 1, 7, 7], fill=GOLD)
    d.line([(4, 2), (4, 6)], fill=GOLD_DARK)
    d.line([(3, 3), (5, 3)], fill=GOLD_DARK)
    d.line([(3, 5), (5, 5)], fill=GOLD_DARK)
    return img


def sneak():
    """しゃがんだ人（スニーク）と下向きの矢印。"""
    img, d = canvas(9, 9)
    d.rectangle([1, 2, 3, 4], fill=(214, 170, 130, 255))
    d.rectangle([1, 5, 4, 6], fill=(60, 120, 190, 255))
    d.rectangle([1, 7, 1, 8], fill=(50, 50, 110, 255))
    d.rectangle([4, 7, 4, 8], fill=(50, 50, 110, 255))
    d.line([(7, 1), (7, 6)], fill=INK)
    d.line([(6, 5), (8, 5)], fill=INK)
    d.point((7, 7), fill=INK)
    return img


def click():
    """矢印のカーソル。"""
    img, d = canvas(9, 9)
    d.polygon([(1, 0), (1, 7), (3, 5), (5, 8), (6, 7), (4, 4), (7, 4)], fill=WHITE, outline=INK)
    return img


def clock():
    img, d = canvas(9, 9)
    d.ellipse([0, 0, 8, 8], fill=INK)
    d.ellipse([1, 1, 7, 7], fill=WHITE)
    d.line([(4, 2), (4, 4)], fill=INK)
    d.line([(4, 4), (6, 4)], fill=PIP_RED)
    return img


def hit():
    """カードと +。"""
    img, d = canvas(9, 9)
    rounded(d, [0, 1, 5, 8], WHITE)
    d.line([(7, 2), (7, 6)], fill=(30, 140, 70, 255))
    d.line([(5, 4), (8, 4)], fill=(30, 140, 70, 255))
    d.point((6, 4), fill=(30, 140, 70, 255))
    return img


def stand():
    """手のひら（止める）。"""
    img, d = canvas(9, 9)
    skin, edge = (230, 186, 140, 255), (150, 100, 70, 255)
    d.rectangle([1, 4, 7, 8], fill=skin, outline=edge)
    for x in (1, 3, 5):
        d.rectangle([x, 0, x + 1, 4], fill=skin, outline=edge)
    d.rectangle([7, 2, 8, 5], fill=skin, outline=edge)
    return img


def double():
    """チップ ×2。"""
    img, d = canvas(9, 9)
    d.ellipse([0, 3, 4, 7], fill=OUTLINE)
    d.ellipse([1, 4, 3, 6], fill=CHIP_RED)
    label(img, "2", 6, 2, INK)
    d.point((5, 4), fill=INK)
    return img


def split():
    """2 枚のカードを左右に分ける。"""
    img, d = canvas(9, 9)
    rounded(d, [0, 2, 3, 8], WHITE)
    rounded(d, [5, 2, 8, 8], WHITE)
    d.point((1, 0), fill=INK)
    d.point((0, 1), fill=INK)
    d.point((7, 0), fill=INK)
    d.point((8, 1), fill=INK)
    return img


def arrow():
    img, d = canvas(7, 9)
    d.line([(0, 4), (5, 4)], fill=INK)
    d.line([(3, 2), (5, 4)], fill=INK)
    d.line([(3, 6), (5, 4)], fill=INK)
    return img


ICONS = [("chair", chair), ("chip", chip), ("coin", coin), ("sneak", sneak), ("click", click), ("clock", clock),
         ("hit", hit), ("stand", stand), ("double", double), ("split", split), ("arrow", arrow)]


def card_back():
    img, d = canvas(9, 13)
    rounded(d, [0, 0, 8, 12], (122, 22, 30, 255))
    for y in range(2, 11):
        for x in range(2, 7):
            if (x + y) % 2 == 0:
                img.putpixel((x, y), (168, 50, 56, 255))
    return img


def providers(tex_dir):
    """icons.json に足す bitmap の一覧。絵は tex_dir に書き出す。"""
    out = []

    def add(name, img, code, ascent=8):
        img.save(tex_dir / f"rule_{name}.png")
        out.append({"type": "bitmap", "file": f"luckycasino:font/rule_{name}.png", "ascent": ascent,
                    "height": img.height, "chars": [chr(code)]})

    for n in range(1, 7):
        add(f"die_{n}", die(n), DIE + n - 1)
    add("bowl", bowl(), BOWL)
    for i, (seg, text) in enumerate(WHEEL_SEGMENTS):
        add(f"wheel_{text.lower()}", wheel_tile(seg, text), WHEEL + i)
    for i in range(6):
        add(f"silk_{i + 1}", silk(i), SILK + i)
    for i, bet in enumerate(ROULETTE_BETS):
        add(f"roulette_{bet}", roulette_board(bet), ROULETTE + i)
    for i, (letter, color) in enumerate([("P", BLUE), ("B", (196, 40, 44, 255)), ("T", GREEN)]):
        add(f"baccarat_{letter.lower()}", baccarat_spot(letter, color), BACCARAT + i)
    for i, kind in enumerate(("high", "low", "same")):
        add(f"hl_{kind}", highlow(kind), HIGHLOW + i)
    for i, (name, fn) in enumerate(ICONS):
        add(name, fn(), ICON + i)
    add("card_back", card_back(), CARD_BACK)
    return out
