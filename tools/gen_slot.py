#!/usr/bin/env python3
"""スロットマシン（筐体・リール・レバー・ランプ）のテクスチャとモデル定義を生成する（Java 版）。

使い方: tools フォルダで python3 gen_slot.py
座標の決まり: 筐体は幅 1 x 奥行 1 x 高さ 2 ブロックで、モデルの 16 単位 = 1 ブロック。プレイヤー側（正面）が +Z、
プレイヤーから見て右が +X。paper の SlotLayout では、どの部品も台の向き + 180 度の ItemDisplay で出すので、
ItemDisplay がモデルを Y 軸まわりに 180 度回して描くのと打ち消し合い、モデルの座標がそのまま台の局所座標になる。
リールの絵柄の並び（STRIPS）は common の Reels と同じにすること。
"""
import math

from PIL import Image, ImageDraw

from gen_table import GOLD, JAVA, LETTERS, write_json
from gen_cards import FONT, plot

SYMBOLS = ["seven", "bar", "bell", "melon", "lemon", "cherry"]
S7, SB, SBELL, SM, SL, SC = range(6)
STRIPS = [
    [S7, SC, SL, SBELL, SM, SB, SL, SC, SBELL, SM, SL, SB, SC, SM, SBELL, SL],
    [S7, SL, SM, SC, SBELL, SB, SL, SM, SC, SBELL, SL, SB, SM, SC, SL, SBELL],
    [S7, SBELL, SL, SM, SC, SB, SL, SBELL, SM, SL, SB, SC, SBELL, SL, SM, SL],
]
STOPS = 16

# リール: 16 角柱。軸はモデルの (y, z) = (8, 8)、半径 R、1 コマの高さ FACE_H、幅 REEL_W（モデル単位）
R = 6.4
FACE_H = 2 * R * math.tan(math.radians(180 / STOPS))
REEL_W = 3.4
# 筐体の中でのリールの軸の高さ（モデル単位、床 = 0）と、3 本の中心の x
AXIS_Y = 21
REEL_X = [3.9, 8, 12.1]
# 窓に見える上下の段の中心と中央の段の高さの差（当たりの帯をずらす量。paper の SlotLayout.ROW と同じ）
ROW = 2.4

# 絵柄のテクスチャ: 96 x 96、1 コマ 24 x 16 px。左の列が止まっているとき、2 列目が回っているとき（ぶれ）
CELL_W, CELL_H = 24, 16
PAPER = (246, 240, 224, 255)
PAPER_EDGE = (214, 204, 184, 255)
OUTLINE = (40, 30, 30, 255)
RED = (206, 36, 40, 255)
RED_DARK = (140, 20, 26, 255)
YELLOW = (250, 214, 60, 255)
YELLOW_DARK = (196, 150, 30, 255)
GREEN = (56, 150, 60, 255)
GREEN_DARK = (30, 96, 40, 255)
BLACK = (34, 34, 40, 255)
WHITE = (248, 248, 244, 255)
GOLD_DARK = (168, 124, 44, 255)

BODY = (150, 26, 36, 255)
BODY_DARK = (104, 16, 24, 255)
BODY_LIGHT = (178, 44, 52, 255)


def draw_seven(img, ox, oy):
    d = ImageDraw.Draw(img)
    # 太い 7（縁取り付き）
    d.polygon([(ox + 6, oy + 2), (ox + 18, oy + 2), (ox + 18, oy + 5), (ox + 12, oy + 14), (ox + 8, oy + 14),
               (ox + 14, oy + 5), (ox + 6, oy + 5)], fill=RED, outline=OUTLINE)
    d.line([(ox + 7, oy + 3), (ox + 16, oy + 3)], fill=(255, 120, 110, 255))


def draw_bar(img, ox, oy):
    d = ImageDraw.Draw(img)
    d.rectangle([ox + 3, oy + 4, ox + 20, oy + 11], fill=BLACK, outline=OUTLINE)
    x = ox + 6
    for ch in "BAR":
        plot(img, LETTERS["A"] if ch == "A" else LETTERS[ch], x, oy + 5, WHITE)
        x += 4
    d.line([(ox + 3, oy + 3), (ox + 20, oy + 3)], fill=GOLD)
    d.line([(ox + 3, oy + 12), (ox + 20, oy + 12)], fill=GOLD)


def draw_bell(img, ox, oy):
    d = ImageDraw.Draw(img)
    d.polygon([(ox + 11, oy + 2), (ox + 13, oy + 2), (ox + 16, oy + 5), (ox + 17, oy + 10), (ox + 19, oy + 12),
               (ox + 5, oy + 12), (ox + 7, oy + 10), (ox + 8, oy + 5)], fill=YELLOW, outline=YELLOW_DARK)
    d.line([(ox + 10, oy + 4), (ox + 9, oy + 9)], fill=(255, 246, 180, 255))
    d.rectangle([ox + 11, oy + 12, ox + 13, oy + 14], fill=YELLOW_DARK)


def draw_melon(img, ox, oy):
    d = ImageDraw.Draw(img)
    # 半月の切り身（下が皮）
    d.pieslice([ox + 3, oy - 6, ox + 20, oy + 14], 0, 180, fill=GREEN_DARK)
    d.pieslice([ox + 4, oy - 5, ox + 19, oy + 12], 0, 180, fill=(240, 236, 210, 255))
    d.pieslice([ox + 5, oy - 4, ox + 18, oy + 11], 0, 180, fill=RED)
    for sx, sy in ((8, 6), (11, 8), (14, 6), (11, 5)):
        img.putpixel((ox + sx, oy + sy), BLACK)


def draw_lemon(img, ox, oy):
    d = ImageDraw.Draw(img)
    d.ellipse([ox + 5, oy + 3, ox + 18, oy + 12], fill=YELLOW, outline=YELLOW_DARK)
    d.point([(ox + 4, oy + 7), (ox + 19, oy + 7)], fill=YELLOW_DARK)
    d.line([(ox + 8, oy + 5), (ox + 11, oy + 4)], fill=(255, 246, 180, 255))


def draw_cherry(img, ox, oy):
    d = ImageDraw.Draw(img)
    d.line([(ox + 8, oy + 9), (ox + 12, oy + 2)], fill=GREEN_DARK)
    d.line([(ox + 15, oy + 9), (ox + 12, oy + 2)], fill=GREEN_DARK)
    d.line([(ox + 12, oy + 2), (ox + 15, oy + 1)], fill=GREEN)
    for cx in (8, 15):
        d.ellipse([ox + cx - 3, oy + 8, ox + cx + 3, oy + 14], fill=RED, outline=RED_DARK)
        img.putpixel((ox + cx - 1, oy + 10), (255, 150, 150, 255))


DRAW = [draw_seven, draw_bar, draw_bell, draw_melon, draw_lemon, draw_cherry]


def symbol_cell(index):
    img = Image.new("RGBA", (CELL_W, CELL_H), PAPER)
    DRAW[index](img, 0, 0)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (CELL_W - 1, 0)], fill=PAPER_EDGE)
    d.line([(0, CELL_H - 1), (CELL_W - 1, CELL_H - 1)], fill=PAPER_EDGE)
    return img


def blurred(cell):
    """縦方向のぶれ（回っているときの絵）。上下 3 px ずつの平均を地の色と混ぜる。"""
    out = Image.new("RGBA", cell.size, PAPER)
    for x in range(cell.width):
        for y in range(cell.height):
            acc = [0, 0, 0]
            for k in range(-3, 4):
                p = cell.getpixel((x, (y + k) % cell.height))
                for c in range(3):
                    acc[c] += p[c]
            px = tuple(int((acc[c] / 7) * 0.7 + PAPER[c] * 0.3) for c in range(3)) + (255,)
            out.putpixel((x, y), px)
    return out


def symbols_texture():
    img = Image.new("RGBA", (96, 96), PAPER)
    for i in range(len(SYMBOLS)):
        cell = symbol_cell(i)
        img.paste(cell, (0, i * CELL_H))
        img.paste(blurred(cell), (CELL_W, i * CELL_H))
    return img


def cell_uv(symbol, blur):
    u0 = (CELL_W * (1 if blur else 0)) * 16 / 96
    v0 = symbol * CELL_H * 16 / 96
    return [round(u0, 4), round(v0, 4), round(u0 + CELL_W * 16 / 96, 4), round(v0 + CELL_H * 16 / 96, 4)]


def reel_model(strip, blur):
    """16 角柱のリール。コマ k はリールの正面から上へ k x 22.5 度。要素は面 1 枚ずつで、
    正面・上・背面・下のどれかの向きの板を ±45 度まで X 軸まわりに回して置く（正の角度で正面の板が下へ回る）。"""
    x0, x1 = 8 - REEL_W / 2, 8 + REEL_W / 2
    hh = FACE_H / 2
    lo, hi = 8 - hh, 8 + hh
    elements = []
    for k in range(STOPS):
        theta = k * 360 / STOPS
        base = min((0, 90, 180, 270, 360), key=lambda b: abs(b - theta)) % 360
        angle = (base - theta + 180) % 360 - 180
        uv = cell_uv(strip[k], blur)
        if base == 0:
            frm, to, face = [x0, lo, 8 + R], [x1, hi, 8 + R], "south"
        elif base == 90:
            frm, to, face = [x0, 8 + R, lo], [x1, 8 + R, hi], "up"
        elif base == 180:
            # 背面の板は正面へ回ってきたとき上下左右が逆になるので、UV を 180 度回す
            frm, to, face = [x0, lo, 8 - R], [x1, hi, 8 - R], "north"
            uv = [uv[2], uv[3], uv[0], uv[1]]
        else:
            frm, to, face = [x0, 8 - R, lo], [x1, 8 - R, hi], "down"
        element = {
            "from": [round(n, 4) for n in frm], "to": [round(n, 4) for n in to],
            "faces": {face: {"texture": "#symbols", "uv": uv}},
        }
        if angle:
            element["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": angle}
        elements.append(element)
    return {
        "textures": {"symbols": "luckycasino:block/slot_symbols", "particle": "luckycasino:block/slot_symbols"},
        "elements": elements,
    }


def body_texture():
    img = Image.new("RGBA", (16, 16), BODY)
    d = ImageDraw.Draw(img)
    for y in range(16):
        for x in range(16):
            if (x + y * 3) % 7 == 0:
                img.putpixel((x, y), BODY_LIGHT)
            elif (x * 5 + y) % 11 == 0:
                img.putpixel((x, y), BODY_DARK)
    d.rectangle([0, 0, 15, 15], outline=BODY_DARK)
    return img


def panel_texture():
    """下の胴の正面（32 x 32）。上に丸いボタン 3 つ、下にチップの受け皿。"""
    img = Image.new("RGBA", (32, 32), BODY)
    d = ImageDraw.Draw(img)
    for y in range(32):
        for x in range(32):
            if (x + y * 3) % 9 == 0:
                img.putpixel((x, y), BODY_LIGHT)
    d.rectangle([0, 0, 31, 31], outline=GOLD_DARK)
    d.rectangle([2, 2, 29, 9], fill=(40, 26, 26, 255), outline=GOLD)
    for i, color in enumerate([(220, 60, 60, 255), (240, 200, 60, 255), (70, 190, 90, 255)]):
        cx = 8 + i * 8
        d.ellipse([cx - 2, 4, cx + 2, 7], fill=color, outline=OUTLINE)
    text_x = 7
    for ch in "LUCKY":
        plot(img, LETTERS[ch] if ch in LETTERS else FONT[ch.lower()], text_x, 13, GOLD)
        text_x += 4
    d.rectangle([5, 22, 26, 28], fill=(24, 18, 18, 255), outline=GOLD)
    d.line([(6, 23), (25, 23)], fill=(60, 50, 50, 255))
    return img


def marquee_texture():
    """看板（64 x 64 のうち上 64 x 24 を使う）。"""
    img = Image.new("RGBA", (64, 64), (36, 10, 16, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 63, 23], fill=(60, 12, 20, 255), outline=GOLD)
    d.rectangle([2, 2, 61, 21], outline=GOLD_DARK)
    # 大きめの文字（3x5 を 2 倍）
    s = "LUCKY 7"
    x = 32 - (len(s) * 8 - 2) // 2
    for ch in s:
        if ch != " ":
            bitmap = LETTERS.get(ch) or FONT[ch.lower()]
            for by, row in enumerate(bitmap):
                for bx, c in enumerate(row):
                    if c == "#":
                        color = RED if ch == "7" else YELLOW
                        d.rectangle([x + bx * 2, 7 + by * 2, x + bx * 2 + 1, 8 + by * 2], fill=color)
        x += 8
    return img


def face_all(texture, uv=None, skip=()):
    return {f: {"texture": texture, "uv": uv or [0, 0, 16, 16]}
            for f in ("north", "south", "east", "west", "up", "down") if f not in skip}


def box(frm, to, texture, faces=None, uv=None):
    return {"from": frm, "to": to, "faces": faces or face_all(texture, uv)}


def plane(frm, to, texture, sides, uv=None):
    return {"from": frm, "to": to, "faces": {f: {"texture": texture, "uv": uv or [0, 0, 16, 16]} for f in sides}}


def cabinet_model():
    w0, w1 = 2, 14          # 窓の x
    win_lo, win_hi = AXIS_Y - 3.3, AXIS_Y + 3.3
    elements = [
        # 台座
        box([0, 0, 1], [16, 1.5, 16], "#gold"),
        # 下の胴（正面にボタンと受け皿の絵）
        {"from": [1, 1.5, 2], "to": [15, 13, 15], "faces": {
            **face_all("#body", skip=("south",)),
            "south": {"texture": "#panel", "uv": [0, 0, 16, 16]}}},
        # 胴の上の棚（チップの受け皿の上の飾り）
        box([0.5, 12.6, 1.5], [15.5, 13.4, 15.8], "#gold"),
        # 上の胴: 背面と左右の壁（中は空洞でリールが入る）
        box([1, 13.4, 1.5], [15, 27, 2.5], "#body"),
        box([0.5, 13.4, 2], [1.5, 27, 15], "#body"),
        box([14.5, 13.4, 2], [15.5, 27, 15], "#body"),
        # 中の黒い背景と、リールの間の仕切り（両面）
        plane([1.5, 13.4, 2.6], [14.5, 27, 2.6], "#inner", ("south",)),
        plane([5.95, 13.4, 2.6], [5.95, 27, 14.6], "#inner", ("east", "west")),
        plane([10.05, 13.4, 2.6], [10.05, 27, 14.6], "#inner", ("east", "west")),
        # 正面の板（窓のまわり）
        box([1.5, 13.4, 14.6], [14.5, win_lo, 15.4], "#body"),
        box([1.5, win_hi, 14.6], [14.5, 27, 15.4], "#body"),
        box([1.5, win_lo, 14.6], [w0, win_hi, 15.4], "#body"),
        box([w1, win_lo, 14.6], [14.5, win_hi, 15.4], "#body"),
        # 窓の金の縁
        box([w0 - 0.4, win_lo - 0.4, 15.4], [w1 + 0.4, win_lo, 15.7], "#gold"),
        box([w0 - 0.4, win_hi, 15.4], [w1 + 0.4, win_hi + 0.4, 15.7], "#gold"),
        box([w0 - 0.4, win_lo, 15.4], [w0, win_hi, 15.7], "#gold"),
        box([w1, win_lo, 15.4], [w1 + 0.4, win_hi, 15.7], "#gold"),
        # 5 ラインの印（窓の左右の縁に、上・中・下の段の高さで小さな赤い印）
        *[box([x0, AXIS_Y + dy - 0.4, 15.7], [x0 + 0.7, AXIS_Y + dy + 0.4, 15.9], "#line")
          for dy in (-ROW, 0, ROW) for x0 in (w0 - 0.4, w1 - 0.3)],
        # 天板と看板
        box([0.5, 27, 1.5], [15.5, 28, 15.5], "#gold"),
        {"from": [1, 28, 4], "to": [15, 32, 13], "faces": {
            **face_all("#body", skip=("south",)),
            "south": {"texture": "#marquee", "uv": [0, 0, 16, 6]}}},
    ]
    return {
        "textures": {
            "body": "luckycasino:block/slot_body",
            "panel": "luckycasino:block/slot_panel",
            "marquee": "luckycasino:block/slot_marquee",
            "gold": "minecraft:block/gold_block",
            "inner": "minecraft:block/black_concrete",
            "line": "minecraft:block/red_concrete",
            "particle": "luckycasino:block/slot_body",
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 45, 0], "translation": [0, -4.5, 0], "scale": [0.45, 0.45, 0.45]},
            "ground": {"translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.25, 0.25, 0.25]},
        },
    }


def lamps_model(phase):
    """看板の縁の電球。phase 0 と 1 で光る電球が入れ替わる（交互に出して流れるように見せる）。"""
    elements = []
    spots = []
    for i in range(9):
        x = 1.6 + i * 1.6
        spots.append((x, 31.4))
        spots.append((x, 28.6))
    for i, (x, y) in enumerate(spots):
        lit = (i // 2 + phase) % 2 == 0
        elements.append(box([x - 0.35, y - 0.35, 13], [x + 0.35, y + 0.35, 13.5], "#on" if lit else "#off"))
    return {
        "textures": {"on": "minecraft:block/glowstone", "off": "minecraft:block/brown_terracotta",
                     "particle": "minecraft:block/glowstone"},
        "elements": elements,
    }


def winline_model():
    """当たったときにラインに重ねて点滅させる光る帯（中央の段の横線）。上下の段と斜めは、
    paper の SlotMachine.lineTransform で Display の transformation で動かして使う。"""
    return {
        "textures": {"glow": "minecraft:block/shroomlight", "particle": "minecraft:block/shroomlight"},
        "elements": [
            plane([1.8, AXIS_Y - 0.18, 14.55], [14.2, AXIS_Y + 0.18, 14.55], "#glow", ("south",)),
            plane([1.8, AXIS_Y - 1.45, 14.55], [2.1, AXIS_Y + 1.45, 14.55], "#glow", ("south",)),
            plane([13.9, AXIS_Y - 1.45, 14.55], [14.2, AXIS_Y + 1.45, 14.55], "#glow", ("south",)),
        ],
    }


def lever_model():
    """レバー。根元（軸）がモデルの (8, 8, 8)。"""
    return {
        "textures": {"gold": "minecraft:block/gold_block", "arm": "minecraft:block/iron_block",
                     "knob": "minecraft:block/red_concrete", "particle": "minecraft:block/iron_block"},
        "elements": [
            box([6.5, 6.5, 6.5], [9.5, 9.5, 9.5], "#gold"),
            box([7.4, 8, 7.4], [8.6, 18, 8.6], "#arm"),
            box([6.3, 17, 6.3], [9.7, 20.4, 9.7], "#knob"),
        ],
    }


def main():
    tex = JAVA / "textures" / "block"
    tex.mkdir(parents=True, exist_ok=True)
    symbols_texture().save(tex / "slot_symbols.png")
    body_texture().save(tex / "slot_body.png")
    panel_texture().save(tex / "slot_panel.png")
    marquee_texture().save(tex / "slot_marquee.png")
    models = {
        "table/slot": cabinet_model(),
        "slot/lamps_a": lamps_model(0),
        "slot/lamps_b": lamps_model(1),
        "slot/winline": winline_model(),
        "slot/lever": lever_model(),
    }
    for i, strip in enumerate(STRIPS):
        models[f"slot/reel{i + 1}"] = reel_model(strip, False)
        models[f"slot/reel{i + 1}_blur"] = reel_model(strip, True)
    for path, model in models.items():
        write_json(JAVA / "models" / "item" / f"{path}.json", model)
        write_json(JAVA / "items" / f"{path}.json",
                   {"model": {"type": "minecraft:model", "model": f"luckycasino:item/{path}"}})


if __name__ == "__main__":
    main()
