#!/usr/bin/env python3
"""ブラックジャックテーブルとチップのテクスチャ・モデル定義を生成する（Java 版）。

使い方: python3 prototype/tools/gen_table.py
座標の決まりは paper の TableLayout と合わせること（テーブル中心の床 = モデルの (8, 8, 8)、
表示倍率 4/3 で 1 ブロック = 12 モデル単位、プレイヤー側が +Z）。
"""
import json
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


def table_model():
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
            "felt": "luckycasino:block/blackjack_felt",
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


def main():
    tex = JAVA / "textures" / "block"
    tex.mkdir(parents=True, exist_ok=True)
    felt().save(tex / "blackjack_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "blackjack.json", table_model())
    write_json(JAVA / "items" / "table" / "blackjack.json",
               {"model": {"type": "minecraft:model", "model": "luckycasino:item/table/blackjack"}})

    ctex = JAVA / "textures" / "item" / "chip"
    ctex.mkdir(parents=True, exist_ok=True)
    for name, _, base, accent in CHIPS:
        chip_texture(base, accent).save(ctex / f"{name}.png")
        for n in range(1, MAX_STACK + 1):
            model = f"luckycasino:item/chip/{name}_{n}"
            write_json(JAVA / "models" / "item" / "chip" / f"{name}_{n}.json", chip_stack_model(name, n))
            write_json(JAVA / "items" / "chip" / f"{name}_{n}.json", {"model": {"type": "minecraft:model", "model": model}})


if __name__ == "__main__":
    main()
