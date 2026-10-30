#!/usr/bin/env python3
"""ルールブックの絵とカジノのメニューの背景に使うフォント（Java 版のリソースパック）を生成する。

使い方: tools フォルダで python3 gen_font.py
文字の割り当て（私用領域）は paper の Glyphs と同じにすること。

- luckycasino:icons … ルールブックの絵。スロットの絵柄 6 つ（U+E000〜）と、小さなトランプ 52 枚（U+E100〜、
  番号 = スート × 13 + ランク、並びは gen_cards.py の SUITS・RANKS と同じ）と、ほかのゲームの絵（gen_rule_art.py）
- luckycasino:menu … チェストのメニューの背景（3 段 U+E200、6 段 U+E201）と、文字送りを戻す空白（U+F001・U+F002）。
  タイトルの先頭で左へ 8 戻して背景を描き、また戻してからタイトルの文字を書く
"""
from PIL import Image, ImageDraw

import gen_rule_art
from gen_cards import FONT, OUTLINE, RANKS, SMALL, SUITS, plot, rank_bitmap
from gen_slot import CELL_H, CELL_W, symbol_cell, SYMBOLS
from gen_table import GOLD, JAVA, write_json

TEX = JAVA / "textures" / "font"

# 小さなトランプ（9 x 13）
CARD_W, CARD_H = 9, 13
CARD_FACE = (246, 240, 224, 255)

# メニューの背景の色
FELT = (30, 104, 60, 255)
FELT_DARK = (22, 84, 48, 255)
FELT_LIGHT = (40, 120, 70, 255)
RIM = (120, 20, 30, 255)
RIM_DARK = (84, 12, 20, 255)
GOLD_DARK = (168, 124, 44, 255)
PLATE = (236, 226, 200, 255)
PLATE_EDGE = (196, 178, 140, 255)
SLOT = (16, 52, 30, 255)
SLOT_SHADE = (10, 36, 20, 255)
SLOT_LIGHT = (70, 140, 92, 255)

ICON_SLOT = 0xE000
ICON_CARD = 0xE100
MENU_27 = 0xE200
MENU_54 = 0xE201
SPACE_TO_EDGE = 0xF001
SPACE_BACK = 0xF002
MENU_W = 176


def slot_icon(index):
    """スロットの絵柄（リールの 1 コマから上下の縁を除いた 22 x 14）。"""
    return symbol_cell(index).crop((1, 1, CELL_W - 1, CELL_H - 1))


def mini_card(suit_idx, rank_idx):
    suit, color = SUITS[suit_idx]
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, CARD_W - 1, CARD_H - 1], fill=OUTLINE)
    d.rectangle([1, 1, CARD_W - 2, CARD_H - 2], fill=CARD_FACE)
    for x, y in [(0, 0), (CARD_W - 1, 0), (0, CARD_H - 1), (CARD_W - 1, CARD_H - 1)]:
        img.putpixel((x, y), (0, 0, 0, 0))
    label = rank_bitmap(RANKS[rank_idx])
    plot(img, label, (CARD_W - len(label[0])) // 2, 1, color)
    plot(img, SMALL[suit], 2, 7, color)
    return img


def menu_background(rows):
    """チェストのメニュー（rows 段）の背景。マスの位置はバニラの generic_54 と同じ。
    タイトル（y = 6）と「インベントリ」（y = 高さ − 94）の文字はバニラの暗い色のままなので、その下は明るい札にする。"""
    h = 114 + rows * 18
    img = Image.new("RGBA", (MENU_W, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 外枠（赤い縁と金の線）。角は 1 px 落とす
    d.rectangle([0, 0, MENU_W - 1, h - 1], fill=RIM_DARK)
    d.rectangle([1, 1, MENU_W - 2, h - 2], fill=RIM)
    d.rectangle([3, 3, MENU_W - 4, h - 4], fill=GOLD)
    d.rectangle([4, 4, MENU_W - 5, h - 5], fill=FELT)
    for x, y in [(0, 0), (MENU_W - 1, 0), (0, h - 1), (MENU_W - 1, h - 1)]:
        img.putpixel((x, y), (0, 0, 0, 0))
    # フェルトの細かい模様
    for y in range(4, h - 4):
        for x in range(4, MENU_W - 4):
            if (x * 3 + y * 7) % 13 == 0:
                img.putpixel((x, y), FELT_DARK)
            elif (x * 5 + y) % 17 == 0:
                img.putpixel((x, y), FELT_LIGHT)
    # 文字の下の札
    label_y = h - 94

    def plate(y0, y1):
        d.rectangle([6, y0, MENU_W - 7, y1], fill=PLATE_EDGE)
        d.rectangle([7, y0 + 1, MENU_W - 8, y1 - 1], fill=PLATE)

    plate(4, 15)
    plate(label_y - 2, label_y + 9)
    # マス（18 x 18、バニラと同じ位置）

    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=SLOT)
        d.line([(x, y), (x + 16, y)], fill=SLOT_SHADE)
        d.line([(x, y), (x, y + 16)], fill=SLOT_SHADE)
        d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=SLOT_LIGHT)
        d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=SLOT_LIGHT)

    for r in range(rows):
        for c in range(9):
            slot(7 + c * 18, 17 + r * 18)
    inv_y = 18 + rows * 18 + 13 - 1
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, inv_y + r * 18)
    for c in range(9):
        slot(7 + c * 18, inv_y + 58)
    # 上の段と持ち物の間に金の線
    d.line([(7, inv_y - 14), (MENU_W - 8, inv_y - 14)], fill=GOLD_DARK)
    return img


def char(code):
    return chr(code)


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    icons = []
    for i, name in enumerate(SYMBOLS):
        path = f"slot_{name}.png"
        slot_icon(i).save(TEX / path)
        icons.append({"type": "bitmap", "file": f"luckycasino:font/{path}", "ascent": 9, "height": 14,
                      "chars": [char(ICON_SLOT + i)]})
    sheet = Image.new("RGBA", (CARD_W * 13, CARD_H * 4), (0, 0, 0, 0))
    for s in range(4):
        for r in range(13):
            sheet.paste(mini_card(s, r), (r * CARD_W, s * CARD_H))
    sheet.save(TEX / "cards.png")
    icons.append({"type": "bitmap", "file": "luckycasino:font/cards.png", "ascent": 8, "height": CARD_H,
                  "chars": ["".join(char(ICON_CARD + s * 13 + r) for r in range(13)) for s in range(4)]})
    icons += gen_rule_art.providers(TEX)
    write_json(JAVA / "font" / "icons.json", {"providers": icons})

    menu_background(3).save(TEX / "menu_27.png")
    menu_background(6).save(TEX / "menu_54.png")
    write_json(JAVA / "font" / "menu.json", {"providers": [
        {"type": "space", "advances": {char(SPACE_TO_EDGE): -8, char(SPACE_BACK): -(MENU_W + 1 - 8)}},
        # タイトルの文字の上端は y = 6 なので、ascent 13 で背景の上端がメニューの上端にそろう
        {"type": "bitmap", "file": "luckycasino:font/menu_27.png", "ascent": 13, "height": 114 + 3 * 18,
         "chars": [char(MENU_27)]},
        {"type": "bitmap", "file": "luckycasino:font/menu_54.png", "ascent": 13, "height": 114 + 6 * 18,
         "chars": [char(MENU_54)]},
    ]})


if __name__ == "__main__":
    main()
