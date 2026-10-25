#!/usr/bin/env python3
"""テーブル（ブラックジャック・ルーレット・ポーカー）とチップのテクスチャ・モデル定義を生成する（Java 版）。

使い方: python3 tools/gen_table.py
座標の決まりは paper の各 Layout と合わせること（テーブル中心の床 = モデルの (8, 8, 8)、
表示倍率 4/3 で 1 ブロック = 12 モデル単位、プレイヤー側が +Z、プレイヤーから見て右が +X）。
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

from gen_cards import FONT, plot

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "resourcepacks" / "java" / "assets" / "luckycasino"

FELT = (30, 110, 62, 255)
FELT_DARK = (24, 92, 52, 255)
FELT_LIGHT = (38, 124, 70, 255)
GOLD = (222, 176, 72, 255)
WHITE = (236, 236, 228, 255)

# 3x5 の英大文字（FONT にない分）。テーブルの文字に使う
LETTERS = {
    "B": ["##.", "#.#", "##.", "#.#", "##."], "L": ["#..", "#..", "#..", "#..", "###"],
    "C": [".##", "#..", "#..", "#..", ".##"], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "S": [".##", "#..", ".#.", "..#", "##."],
    "T": ["###", ".#.", ".#.", ".#.", ".#."], "O": [".#.", "#.#", "#.#", "#.#", ".#."],
    "E": ["###", "#..", "##.", "#..", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "N": ["#.#", "###", "###", "#.#", "#.#"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "X": ["#.#", "#.#", ".#.", "#.#", "#.#"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "M": ["#.#", "###", "###", "#.#", "#.#"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"],
    "-": ["...", "...", "###", "...", "..."], ":": [".", "#", ".", "#", "."],
    "A": FONT["a"], "J": FONT["j"], "K": FONT["k"],
}
GLYPHS = {**{k: v for k, v in FONT.items() if k.isdigit()}, **LETTERS}

# 1 ブロック = 24 ピクセル（12 モデル単位 × 2）。フェルトは 4 x 2 ブロック = 96 x 48
PX = 24
SEATS = 5


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def text(img, s, cx, y, color):
    width = sum(len(GLYPHS[ch][0]) + 1 for ch in s if ch != " ") + 3 * s.count(" ") - 1
    x = cx - width // 2
    for ch in s:
        if ch == " ":
            x += 3
            continue
        plot(img, GLYPHS[ch], x, y, color)
        x += len(GLYPHS[ch][0]) + 1


def u(x_blocks):
    return round((x_blocks + 2) * PX)


def v(z_blocks):
    return round((z_blocks + 1) * PX)


def felt_base(width, height):
    """フェルトの地。上半分（width x height）だけ UV で使う。"""
    img = Image.new("RGBA", (width, width), FELT)
    for y in range(height):
        for x in range(width):
            n = (x * 7 + y * 13 + (x * y) % 5) % 11
            if n == 0:
                img.putpixel((x, y), FELT_DARK)
            elif n == 5:
                img.putpixel((x, y), FELT_LIGHT)
    ImageDraw.Draw(img).rectangle([0, height, width - 1, width - 1], fill=FELT_DARK)
    return img


def felt():
    img = Image.new("RGBA", (96, 96), FELT)
    d = ImageDraw.Draw(img)
    # 市松の細かいムラでフェルトらしく
    for y in range(48):
        for x in range(96):
            n = (x * 7 + y * 13 + (x * y) % 5) % 11
            if n == 0:
                img.putpixel((x, y), FELT_DARK)
            elif n == 5:
                img.putpixel((x, y), FELT_LIGHT)
    d.rectangle([2, 2, 93, 45], outline=GOLD)
    text(img, "BLACKJACK PAYS 3 TO 2", 48, 19, GOLD)
    # ディーラーのカード置き場
    d.rectangle([u(-0.45), v(-0.78), u(0.45), v(-0.32)], outline=FELT_LIGHT)
    for i in range(SEATS):
        x = 1.6 - 0.8 * i
        # カード置き場と賭け金の丸
        d.rectangle([u(x - 0.17), v(0.05), u(x + 0.17), v(0.45)], outline=WHITE)
        cx, cy = u(x), v(0.68)
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=GOLD)
    # 下半分は余白（UV は上半分だけ使う）
    d.rectangle([0, 48, 95, 95], fill=FELT_DARK)
    return img


def face(texture, uv):
    return {"texture": texture, "uv": uv}


def box(frm, to, top, side, top_uv=None):
    faces = {}
    for f in ("north", "south", "east", "west", "down"):
        faces[f] = face(side, [0, 0, 16, 16])
    faces["up"] = face(top, top_uv or [0, 0, 16, 16])
    return {"from": frm, "to": to, "faces": faces}


def table_model(felt_name):
    wood, felt_tex = "#wood", "#felt"
    elements = [
        # フェルトの天板（上面 y=19.4 → 床から 0.95 ブロック）
        box([-16, 18.4, -4], [32, 19.4, 20], felt_tex, wood, [0, 0, 16, 8]),
        # 縁（木）
        box([-16, 18.4, 19], [32, 20.2, 20], wood, wood),
        box([-16, 18.4, -4], [32, 19.9, -3], wood, wood),
        box([-16, 18.4, -3], [-15, 20.2, 19], wood, wood),
        box([31, 18.4, -3], [32, 20.2, 19], wood, wood),
        # 胴と脚
        box([-14, 15.5, -2], [30, 18.4, 18], wood, wood),
    ]
    for lx in (-13, 27):
        for lz in (-1, 15):
            elements.append(box([lx, 8, lz], [lx + 2, 15.5, lz + 2], wood, wood))
    return {
        "textures": {
            "felt": f"luckycasino:block/{felt_name}",
            "wood": "minecraft:block/dark_oak_planks",
            "particle": "minecraft:block/dark_oak_planks",
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.22, 0.22, 0.22]},
            "ground": {"translation": [0, 2, 0], "scale": [0.15, 0.15, 0.15]},
            "fixed": {"scale": [0.25, 0.25, 0.25]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 0], "scale": [0.12, 0.12, 0.12]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.15, 0.15, 0.15]},
        },
    }


# チップ: (名前, 額, 地の色, 模様の色)
CHIPS = [
    ("c10", 10, (232, 232, 224, 255), (52, 92, 196, 255)),
    ("c50", 50, (196, 40, 44, 255), (240, 240, 232, 255)),
    ("c100", 100, (40, 140, 72, 255), (240, 240, 232, 255)),
    ("c500", 500, (40, 40, 48, 255), (222, 176, 72, 255)),
    ("c1000", 1000, (128, 60, 168, 255), (240, 222, 120, 255)),
]
MAX_STACK = 10


def chip_texture(base, accent):
    # 上面 8x8 を (0,0)、側面の縞 8x1 を (0,8) に置く
    img = Image.new("RGBA", (16, 16), base)
    top = ["..#..#..", "........", "#..##..#", "..#..#..", "..#..#..", "#..##..#", "........", "..#..#.."]
    plot(img, top, 0, 0, accent)
    for x in (1, 4, 6):
        img.putpixel((x, 8), accent)
    return img


def chip_stack_model(name, count):
    tex = f"#chip"
    elements = []
    for i in range(count):
        y = 8 + i
        side = {"texture": tex, "uv": [0, 8, 8, 9]}
        elements.append({
            "from": [4, y, 4], "to": [12, y + 1, 12],
            "faces": {
                "up": {"texture": tex, "uv": [0, 0, 8, 8]},
                "down": {"texture": tex, "uv": [0, 0, 8, 8]},
                "north": side, "south": side, "east": side, "west": side,
            },
        })
    return {
        "textures": {"chip": f"luckycasino:item/chip/{name}", "particle": f"luckycasino:item/chip/{name}"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 45, 0], "translation": [0, 3, 0], "scale": [1.4, 1.4, 1.4]},
            "ground": {"scale": [0.8, 0.8, 0.8]},
        },
    }


# ---- ルーレット ----
# フェルトは 4 px / モデル単位（1 ブロック = 48 px、192 x 96）。数値は paper の RouletteLayout と合わせる
RPX = 48
MAT_U, MAT_V, CELL = 58, 30, 9
WHEEL_U, WHEEL_V, WHEEL_R = 28, 48, 24
WHEEL_ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
               16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
R_RED = (176, 30, 36, 255)
R_BLACK = (34, 32, 38, 255)
R_GREEN = (24, 128, 64, 255)
WOOD_DARK = (74, 46, 26, 255)
WOOD = (110, 70, 40, 255)


def number_color(n):
    return R_GREEN if n == 0 else (R_RED if n in REDS else R_BLACK)


def text_width(s):
    return sum(len(GLYPHS[ch][0]) + 1 for ch in s if ch != " ") + 3 * s.count(" ") - 1


def cell(d, img, x0, y0, w, h, fill, label):
    d.rectangle([x0, y0, x0 + w, y0 + h], fill=fill, outline=WHITE)
    if label:
        text(img, label, x0 + w // 2 + 1, y0 + (h - 5) // 2 + 1, WHITE)


def roulette_felt():
    img = felt_base(192, 96)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 189, 93], outline=GOLD)
    # 0
    cell(d, img, MAT_U, MAT_V, CELL, CELL * 3, R_GREEN, "0")
    for c in range(12):
        for t in range(3):
            n = 3 * c + (3 - t)
            cell(d, img, MAT_U + CELL + CELL * c, MAT_V + CELL * t, CELL, CELL, number_color(n), str(n))
    for t in range(3):
        cell(d, img, MAT_U + CELL * 13, MAT_V + CELL * t, CELL, CELL, FELT, "2:1")
    for i, label in enumerate(["1-12", "13-24", "25-36"]):
        cell(d, img, MAT_U + CELL + 36 * i, MAT_V + CELL * 3, 36, CELL, FELT, label)
    for i, label in enumerate(["1-18", "EVEN", "", "", "ODD", "19-36"]):
        x0 = MAT_U + CELL + 18 * i
        cell(d, img, x0, MAT_V + CELL * 4, 18, CELL, FELT, label)
        if i in (2, 3):
            color = R_RED if i == 2 else R_BLACK
            cx, cy = x0 + 9, MAT_V + CELL * 4 + 4
            d.polygon([(cx, cy - 3), (cx + 4, cy), (cx, cy + 3), (cx - 4, cy)], fill=color, outline=WHITE)
    # ホイールの受け皿
    d.ellipse([WHEEL_U - WHEEL_R - 3, WHEEL_V - WHEEL_R - 3, WHEEL_U + WHEEL_R + 3, WHEEL_V + WHEEL_R + 3], fill=WOOD_DARK, outline=GOLD)
    text(img, "ROULETTE", 128, 12, GOLD)
    return img


def wheel_texture():
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dz = x - c, y - c
            r = math.hypot(dx, dz)
            if r > 31.5:
                continue
            if r > 29:
                img.putpixel((x, y), WOOD)
                continue
            if r < 12:
                img.putpixel((x, y), GOLD if r < 4 or (r > 9 and r < 10.5) else WOOD)
                continue
            # 時計回り（上から見て）に -Z から測った角度
            angle = math.degrees(math.atan2(dx, -dz)) % 360
            index = int((angle + 360 / 74) // (360 / 37)) % 37
            n = WHEEL_ORDER[index]
            color = number_color(n)
            if r > 26 or r < 15:
                color = tuple(min(255, ch + 40) for ch in color[:3]) + (255,) if n != 0 else (40, 160, 80, 255)
            edge = abs(((angle + 360 / 74) % (360 / 37)) - 0) < 0.6
            img.putpixel((x, y), (200, 200, 200, 255) if edge and r > 15 else color)
    return img


def wheel_model():
    tex = "#wheel"
    return {
        "textures": {"wheel": "luckycasino:block/roulette_wheel", "gold": "minecraft:block/gold_block",
                     "particle": "luckycasino:block/roulette_wheel"},
        "elements": [{
            "from": [0, 8, 0], "to": [16, 8.5, 16],
            "faces": {
                "up": {"texture": tex, "uv": [0, 0, 16, 16]},
                "down": {"texture": tex, "uv": [0, 0, 16, 16]},
            },
        }, {
            # 中央の取っ手
            "from": [7.25, 8.5, 7.25], "to": [8.75, 10, 8.75],
            "faces": {f: {"texture": "#gold", "uv": [0, 0, 16, 16]} for f in ("up", "north", "south", "east", "west")},
        }],
    }


def ball_model():
    faces = {f: {"texture": "#ball", "uv": [0, 0, 16, 16]} for f in ("up", "down", "north", "south", "east", "west")}
    return {
        "textures": {"ball": "minecraft:block/white_concrete", "particle": "minecraft:block/white_concrete"},
        "elements": [{"from": [7, 7, 7], "to": [9, 9, 9], "faces": faces}],
    }


# ---- ポーカー ----
POKER_SEATS = [(2.55, -0.2), (1.2, 1.55), (0.4, 1.55), (-0.4, 1.55), (-1.2, 1.55), (-2.55, -0.2)]


def poker_card_spot(i):
    """席 i の手札の位置（paper の PokerLayout.holeCard と合わせる）。"""
    x, z = POKER_SEATS[i]
    if i == 0:
        return 1.55, -0.2
    if i == 5:
        return -1.55, -0.2
    return x, 0.45


def poker_felt():
    img = felt_base(96, 48)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, 93, 45], radius=8, outline=GOLD)
    # 共有カード 5 枚の枠
    for k in range(5):
        x = -0.6 + 0.3 * k
        d.rectangle([u(x - 0.12), v(-0.38), u(x + 0.12), v(-0.02)], outline=FELT_LIGHT)
    text(img, "TEXAS HOLDEM", 48, 6, GOLD)
    cx, cy = u(0), v(0.2)
    d.ellipse([cx - 4, cy - 3, cx + 4, cy + 3], outline=GOLD)
    for i in range(6):
        x, z = poker_card_spot(i)
        if i in (0, 5):
            d.rectangle([u(x - 0.2), v(z - 0.15), u(x + 0.2), v(z + 0.15)], outline=WHITE)
        else:
            d.rectangle([u(x - 0.17), v(z - 0.15), u(x + 0.17), v(z + 0.15)], outline=WHITE)
    return img


def dealer_button_texture():
    img = Image.new("RGBA", (16, 16), WHITE)
    plot(img, LETTERS["D"], 3, 2, BLACK_TEXT)
    for x in range(8):
        img.putpixel((x, 8), (180, 180, 180, 255) if x % 3 == 0 else WHITE)
    return img


BLACK_TEXT = (30, 30, 36, 255)


def for_item_display(img, used_height=None):
    """ItemDisplay はモデルを Y 軸まわりに 180 度回して描く（23w16a 以降）。
    フェルトやホイールの絵はテーブルの局所座標（u → +X、v → +Z）で描いているので、
    UV で使う範囲（上から used_height 行）を 180 度回しておくと、ゲーム内で局所座標どおりの位置・向きに見える。"""
    h = used_height or img.height
    out = img.copy()
    out.paste(img.crop((0, 0, img.width, h)).rotate(180), (0, 0))
    return out


def main():
    tex = JAVA / "textures" / "block"
    tex.mkdir(parents=True, exist_ok=True)
    for_item_display(felt(), 48).save(tex / "blackjack_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "blackjack.json", table_model("blackjack_felt"))
    write_json(JAVA / "items" / "table" / "blackjack.json",
               {"model": {"type": "minecraft:model", "model": "luckycasino:item/table/blackjack"}})

    for_item_display(roulette_felt(), 96).save(tex / "roulette_felt.png")
    for_item_display(wheel_texture()).save(tex / "roulette_wheel.png")
    write_json(JAVA / "models" / "item" / "table" / "roulette.json", table_model("roulette_felt"))
    write_json(JAVA / "models" / "item" / "roulette" / "wheel.json", wheel_model())
    write_json(JAVA / "models" / "item" / "roulette" / "ball.json", ball_model())
    for_item_display(poker_felt(), 48).save(tex / "poker_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "poker.json", table_model("poker_felt"))
    for path in ("table/roulette", "table/poker", "roulette/wheel", "roulette/ball", "poker/button"):
        write_json(JAVA / "items" / f"{path}.json",
                   {"model": {"type": "minecraft:model", "model": f"luckycasino:item/{path}"}})

    ctex = JAVA / "textures" / "item" / "chip"
    ctex.mkdir(parents=True, exist_ok=True)
    for name, _, base, accent in CHIPS:
        chip_texture(base, accent).save(ctex / f"{name}.png")
        for n in range(1, MAX_STACK + 1):
            model = f"luckycasino:item/chip/{name}_{n}"
            write_json(JAVA / "models" / "item" / "chip" / f"{name}_{n}.json", chip_stack_model(name, n))
            write_json(JAVA / "items" / "chip" / f"{name}_{n}.json", {"model": {"type": "minecraft:model", "model": model}})
    for_item_display(dealer_button_texture()).save(ctex / "dealer.png")
    write_json(JAVA / "models" / "item" / "poker" / "button.json", chip_stack_model("dealer", 1))


if __name__ == "__main__":
    main()
