#!/usr/bin/env python3
"""実績のアイコン（Java 版のアイテムモデル）を生成する。

使い方: tools フォルダで python3 gen_icons.py
カードはトランプのテクスチャ（gen_cards）、スロットの絵柄はリールのテクスチャ（gen_slot）と同じ絵を使い、
実績ごとに中身がわかる 72x72 の絵にする。paper の Achievements の icon は "achievement/<名前>" を指す。
"""
import math

from PIL import Image, ImageDraw

from gen_cards import FONT, RANKS, SUITS, front, plot
from gen_slot import CELL_H, CELL_W, SYMBOLS, symbol_cell
from gen_table import JAVA, write_json

SIZE = 72
CARD_W, CARD_H = 21, 29

OUTLINE = (40, 30, 30, 255)
GOLD = (250, 206, 72, 255)
GOLD_DARK = (176, 128, 40, 255)
SPARK = (255, 250, 210, 255)
FELT = (34, 112, 64, 255)
FELT_DARK = (22, 78, 44, 255)
WHITE = (248, 248, 244, 255)
RED = (196, 30, 40, 255)
RED_DARK = (130, 18, 26, 255)
BLACK = (34, 34, 40, 255)
GREEN = (40, 150, 70, 255)
GREEN_DARK = (24, 96, 44, 255)
CHROME = (196, 196, 204, 255)
CHROME_DARK = (120, 120, 132, 255)
CABINET = (150, 26, 36, 255)
CABINET_DARK = (104, 16, 24, 255)
SHADOW = (0, 0, 0, 90)


def card(name):
    """"heart_a" のような名前のカードの表。"""
    suit, rank = name.split("_")
    suit_idx = [s for s, _ in SUITS].index(suit)
    return front(suit_idx, RANKS.index(rank))


def scaled(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def shadow_of(img):
    """img の形をした半透明の影。"""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    alpha = img.getchannel("A").point(lambda a: SHADOW[3] if a > 0 else 0)
    out.putalpha(alpha)
    return out


def paste(base, img, x, y, shadow=True):
    x, y = int(round(x)), int(round(y))
    if shadow:
        base.alpha_composite(shadow_of(img), (x + 1, y + 1))
    base.alpha_composite(img, (x, y))


def fan(names, spread=12, tilt=7):
    """カードを扇形に広げて真ん中に置く。tilt は端のカードの傾き（度）。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    n = len(names)
    for i, name in enumerate(names):
        t = 0 if n == 1 else (i - (n - 1) / 2) / ((n - 1) / 2)
        c = card(name).rotate(-t * tilt, resample=Image.NEAREST, expand=True)
        cx = SIZE / 2 + (i - (n - 1) / 2) * spread
        cy = SIZE / 2 + abs(t) * 3 + 2
        paste(img, c, cx - c.width / 2, cy - c.height / 2)
    return img


def sparkle(img, x, y, r=3, color=SPARK):
    for k in range(-r, r + 1):
        a = int(255 * (1 - abs(k) / (r + 1)))
        for px, py in ((x + k, y), (x, y + k)):
            if 0 <= px < SIZE and 0 <= py < SIZE:
                img.alpha_composite(Image.new("RGBA", (1, 1), color[:3] + (a,)), (px, py))


def chip(d, cx, cy, r, color, dark):
    """上から見たカジノチップ（縁に白い目印）。"""
    d.ellipse([cx - r, cy - r + 2, cx + r, cy + r + 2], fill=dark, outline=OUTLINE)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color, outline=OUTLINE)
    for k in range(8):
        a = k * math.pi / 4
        x, y = cx + math.cos(a) * (r - 2.5), cy + math.sin(a) * (r - 2.5)
        d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=WHITE)
    d.ellipse([cx - r * 0.55, cy - r * 0.55, cx + r * 0.55, cy + r * 0.55], outline=WHITE)


def number(img, text, x, y, color, k):
    """3x5 の数字を k 倍で描く（x, y は左上）。"""
    ox = x
    for ch in text:
        glyph = scaled_bitmap(FONT[ch], k)
        plot(img, glyph, ox, y, color)
        ox += len(FONT[ch][0]) * k + k


def number_width(text, k):
    return sum(len(FONT[ch][0]) * k + k for ch in text) - k


def scaled_bitmap(bitmap, k):
    return ["".join(c * k for c in row) for row in bitmap for _ in range(k)]


# ---- アイコン ----

def first_play():
    """カジノデビュー: 伏せたカードと A の上にチップ。"""
    img = fan(["spade_a", "heart_k"], spread=14, tilt=10)
    d = ImageDraw.Draw(img)
    chip(d, 46, 50, 11, RED, RED_DARK)
    chip(d, 26, 54, 9, (40, 70, 170, 255), (24, 40, 110, 255))
    return img


def blackjack():
    img = fan(["spade_a", "spade_k"], spread=16, tilt=10)
    sparkle(img, 12, 14)
    sparkle(img, 60, 12, 2)
    return img


def triple_seven():
    img = fan(["heart_7", "diamond_7", "club_7"], spread=15, tilt=10)
    sparkle(img, 10, 12)
    sparkle(img, 62, 14)
    sparkle(img, 60, 60, 2)
    return img


def five_card():
    return fan(["club_2", "diamond_3", "heart_4", "spade_5", "club_7"], spread=11, tilt=12)


def four_of_a_kind():
    return fan(["spade_k", "heart_k", "diamond_k", "club_k"], spread=13, tilt=10)


def straight_flush():
    return fan(["club_5", "club_6", "club_7", "club_8", "club_9"], spread=11, tilt=12)


def royal_flush():
    img = fan(["heart_10", "heart_j", "heart_q", "heart_k", "heart_a"], spread=11, tilt=12)
    for x, y, r in ((8, 10, 3), (64, 8, 3), (36, 4, 2), (6, 60, 2), (66, 62, 2)):
        sparkle(img, x, y, r, GOLD)
    return img


def bet_cell(label, fill, chip_color, chip_dark):
    """ベットマットの 1 マスに数字と、その上に置いたチップ。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([4, 4, SIZE - 5, SIZE - 5], 6, fill=FELT, outline=FELT_DARK, width=2)
    d.rectangle([14, 10, SIZE - 15, SIZE - 15], fill=fill, outline=WHITE, width=2)
    k = 5
    w = number_width(label, k)
    number(img, label, (SIZE - w) // 2, 16, WHITE, k)
    chip(d, 48, 50, 12, chip_color, chip_dark)
    # ホイールの球
    d.ellipse([12, 46, 22, 56], fill=WHITE, outline=CHROME_DARK)
    img.putpixel((15, 49), (255, 255, 255, 255))
    return img


def straight_up():
    return bet_cell("17", BLACK, RED, RED_DARK)


def green_zero():
    img = bet_cell("0", GREEN, GOLD, GOLD_DARK)
    sparkle(img, 10, 10)
    sparkle(img, 62, 12, 2)
    return img


def reels(symbol):
    """スロットの窓に同じ絵柄が 3 つ並んだところ。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cell = symbol_cell(SYMBOLS.index(symbol))
    w, h = CELL_W - 2, CELL_H
    d.rounded_rectangle([0, 20, SIZE - 1, 20 + h + 16], 6, fill=CABINET, outline=CABINET_DARK, width=2)
    x0 = (SIZE - 3 * w - 4) // 2
    y0 = 28
    d.rectangle([x0 - 2, y0 - 2, x0 + 3 * w + 5, y0 + h + 1], fill=OUTLINE)
    for i in range(3):
        img.alpha_composite(cell.crop((1, 0, CELL_W - 1, CELL_H)), (x0 + i * (w + 2), y0))
    # 当たりの線
    d.line([(x0 - 2, y0 + h // 2), (x0 + 3 * w + 5, y0 + h // 2)], fill=(255, 220, 80, 160))
    # 上のランプ
    for i in range(7):
        x = 8 + i * 9.4
        d.ellipse([x - 2.5, 12, x + 2.5, 17], fill=GOLD if i % 2 == 0 else (255, 120, 60, 255), outline=GOLD_DARK)
    return img


def slot_bars():
    return reels("bar")


def slot_sevens():
    img = reels("seven")
    for x, y, r in ((6, 64, 3), (66, 62, 3), (36, 66, 2)):
        sparkle(img, x, y, r, GOLD)
    return img


def baccarat_tie():
    """痛み分け: 青（プレイヤー）と赤（バンカー）の枠に、同じ 7 点。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 8, 35, 64], 4, fill=(40, 70, 150, 255), outline=(24, 40, 100, 255), width=2)
    d.rounded_rectangle([36, 8, 69, 64], 4, fill=RED, outline=RED_DARK, width=2)
    paste(img, card("spade_7"), 8, 22)
    paste(img, card("heart_7"), 42, 22)
    # 真ん中の「=」
    d.rectangle([30, 30, 41, 33], fill=GOLD, outline=OUTLINE)
    d.rectangle([30, 38, 41, 41], fill=GOLD, outline=OUTLINE)
    return img


def baccarat_pair():
    img = fan(["heart_q", "spade_q"], spread=16, tilt=10)
    d = ImageDraw.Draw(img)
    chip(d, 52, 54, 10, (40, 70, 170, 255), (24, 40, 110, 255))
    sparkle(img, 10, 12)
    sparkle(img, 62, 10, 2)
    return img


ICONS = {
    "first_play": first_play,
    "blackjack": blackjack,
    "triple_seven": triple_seven,
    "five_card": five_card,
    "straight_up": straight_up,
    "green_zero": green_zero,
    "four_of_a_kind": four_of_a_kind,
    "straight_flush": straight_flush,
    "royal_flush": royal_flush,
    "slot_bars": slot_bars,
    "slot_sevens": slot_sevens,
    "baccarat_tie": baccarat_tie,
    "baccarat_pair": baccarat_pair,
}


def main():
    tex = JAVA / "textures" / "item" / "achievement"
    tex.mkdir(parents=True, exist_ok=True)
    for name, draw in ICONS.items():
        draw().save(tex / f"{name}.png")
        write_json(JAVA / "models" / "item" / "achievement" / f"{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"luckycasino:item/achievement/{name}"},
        })
        write_json(JAVA / "items" / "achievement" / f"{name}.json", {
            "model": {"type": "minecraft:model", "model": f"luckycasino:item/achievement/{name}"},
        })
    print(f"実績アイコン {len(ICONS)} 個")


if __name__ == "__main__":
    main()
