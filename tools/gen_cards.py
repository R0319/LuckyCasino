#!/usr/bin/env python3
"""トランプ 52 枚 + 裏面のテクスチャとモデル定義を生成する。

使い方: python3 tools/gen_cards.py
出力先: resourcepacks/java と resourcepacks/bedrock
生成物はリポジトリにコミットするので、ビルド時に Python は不要。
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "resourcepacks" / "java" / "assets" / "luckycasino"
BEDROCK = ROOT / "resourcepacks" / "bedrock"

# ドット絵（マイクラ風）。カード 1 面は 21x29 ピクセル、1 ピクセル = 0.5 モデル単位（1/32 ブロック）。
W, H = 21, 29

CREAM = (240, 232, 214, 255)
CREAM_SHADE = (214, 204, 184, 255)
OUTLINE = (58, 48, 44, 255)
RED = (186, 32, 40, 255)
BLACK = (36, 36, 44, 255)
BACK_RED = (122, 22, 30, 255)
BACK_DARK = (92, 14, 22, 255)
GOLD = (222, 176, 72, 255)
GOLD_DARK = (168, 124, 44, 255)
FRAME = (232, 210, 160, 255)

# スートと色（並び順は common の CardCode と同じにすること。番号 = スート * 13 + ランク + 1、0 は裏向き）
SUITS = [("spade", BLACK), ("heart", RED), ("diamond", RED), ("club", BLACK)]
RANKS = ["a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"]

SMALL = {
    "spade": ["..#..", ".###.", "#####", "..#..", ".###."],
    "heart": [".#.#.", "#####", "#####", ".###.", "..#.."],
    "diamond": ["..#..", ".###.", "#####", ".###.", "..#.."],
    "club": ["..#..", ".###.", "#####", "#.#.#", ".###."],
}
BIG = {
    "spade": ["....#....", "...###...", "..#####..", ".#######.", "#########",
              "#########", ".##.#.##.", "....#....", "...###..."],
    "heart": [".##...##.", "####.####", "#########", "#########", ".#######.",
              "..#####..", "...###...", "....#....", "........."],
    "diamond": ["....#....", "...###...", "..#####..", ".#######.", "#########",
                ".#######.", "..#####..", "...###...", "....#...."],
    "club": ["...###...", "..#####..", "...###...", ".##.#.##.", "#########",
             ".##.#.##.", "....#....", "...###...", "........."],
}
# 3x5 の数字・文字（10 は 1 と 0 を並べる）
FONT = {
    "a": [".#.", "#.#", "###", "#.#", "#.#"], "2": ["##.", "..#", ".#.", "#..", "###"],
    "3": ["##.", "..#", ".#.", "..#", "##."], "4": ["#.#", "#.#", "###", "..#", "..#"],
    "5": ["###", "#..", "##.", "..#", "##."], "6": [".##", "#..", "###", "#.#", "###"],
    "7": ["###", "..#", ".#.", ".#.", ".#."], "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "##."], "1": [".#", "##", ".#", ".#", ".#"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "j": ["..#", "..#", "..#", "#.#", ".#."],
    "q": [".#.", "#.#", "#.#", "#.#", ".##"], "k": ["#.#", "#.#", "##.", "#.#", "#.#"],
}
# 絵札の中央に置く 5x7 の大きい文字と王冠
BIG_LETTER = {
    "j": ["..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."],
    "q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    "k": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
}
CROWN = ["#.#.#", "#####", "#####"]


def plot(img, bitmap, x0, y0, color):
    for y, row in enumerate(bitmap):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x0 + x, y0 + y), color)


def blank(fill, shade):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], fill=OUTLINE)
    d.rectangle([1, 1, W - 2, H - 2], fill=fill)
    # 右と下の内側に影を入れて立体感を出す
    d.line([(2, H - 2), (W - 2, H - 2)], fill=shade)
    d.line([(W - 2, 2), (W - 2, H - 2)], fill=shade)
    for x, y in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:
        img.putpixel((x, y), (0, 0, 0, 0))
    return img


def rank_bitmap(rank):
    if rank != "10":
        return FONT[rank]
    return [a + "." + b for a, b in zip(FONT["1"], FONT["0"])]


def front(suit_idx, rank_idx):
    suit, color = SUITS[suit_idx]
    rank = RANKS[rank_idx]
    img = blank(CREAM, CREAM_SHADE)
    # 隅はランクと小さいスートを横に並べ、中央の絵と重ならないようにする
    label = rank_bitmap(rank)
    corner = Image.new("RGBA", (12, 5), (0, 0, 0, 0))
    plot(corner, label, 0, 0, color)
    plot(corner, SMALL[suit], len(label[0]) + 1, 0, color)
    img.alpha_composite(corner, (2, 2))
    img.alpha_composite(corner.rotate(180), (W - 14, H - 7))
    if rank in ("j", "q", "k"):
        ImageDraw.Draw(img).rectangle([6, 8, 14, 20], fill=FRAME, outline=color)
        plot(img, CROWN, 8, 9, GOLD_DARK)
        plot(img, BIG_LETTER[rank], 8, 12, color)
    else:
        plot(img, BIG[suit], 6, 10, color)
    return img


def back():
    img = blank(BACK_RED, BACK_DARK)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, W - 3, H - 3], outline=GOLD)
    cx, cy = W // 2, H // 2
    # 中心を基準にした斜め格子（上下左右対称）
    for y in range(4, H - 4):
        for x in range(4, W - 4):
            dx, dy = x - cx, y - cy
            if (dx + dy) % 4 == 0 and (dx - dy) % 4 == 0:
                img.putpixel((x, y), GOLD_DARK)
    for dx, dy in [(0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2)]:
        img.putpixel((cx + dx, cy + dy), GOLD)
    img.putpixel((cx, cy), GOLD)
    return img


def card_ids():
    for s, (suit, _) in enumerate(SUITS):
        for r, rank in enumerate(RANKS):
            yield s * 13 + r + 1, f"{suit}_{rank}", s, r


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def bedrock_sheet(front_img, back_img):
    # 64x32: 表 (0,0) / 裏 (32,0) / 側面用の縁の色 (24,0)
    sheet = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    sheet.alpha_composite(front_img, (0, 0))
    sheet.alpha_composite(back_img, (32, 0))
    ImageDraw.Draw(sheet).rectangle([24, 0, 25, 1], fill=OUTLINE)
    return sheet


def main():
    back_img = back()

    # --- Java ---
    tex = JAVA / "textures" / "item" / "card"
    (tex / "front").mkdir(parents=True, exist_ok=True)
    back_img.save(tex / "back.png")
    models = JAVA / "models" / "item" / "card"
    items = JAVA / "items" / "card"
    write_json(models / "back.json", {
        "parent": "luckycasino:item/card_base",
        "textures": {"front": "luckycasino:item/card/back", "back": "luckycasino:item/card/back"},
    })
    write_json(items / "back.json", {"model": {"type": "minecraft:model", "model": "luckycasino:item/card/back"}})

    # --- Bedrock ---
    btex = BEDROCK / "textures" / "entity" / "luckycasino" / "card"
    btex.mkdir(parents=True, exist_ok=True)
    bedrock_sheet(back_img, back_img).save(btex / "hidden.png")
    bedrock_textures = {"c0": "textures/entity/luckycasino/card/hidden"}

    for num, cid, s, r in card_ids():
        img = front(s, r)
        img.save(tex / "front" / f"{cid}.png")
        write_json(models / f"{cid}.json", {
            "parent": "luckycasino:item/card_base",
            "textures": {"front": f"luckycasino:item/card/front/{cid}", "back": "luckycasino:item/card/back"},
        })
        write_json(items / f"{cid}.json", {"model": {"type": "minecraft:model", "model": f"luckycasino:item/card/{cid}"}})
        bedrock_sheet(img, back_img).save(btex / f"{cid}.png")
        bedrock_textures[f"c{num}"] = f"textures/entity/luckycasino/card/{cid}"

    # 統合版のクライアントエンティティとレンダーコントローラー（テクスチャ一覧を含むので生成する）
    write_json(BEDROCK / "entity" / "card.entity.json", {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": "luckycasino:card",
                "materials": {"default": "entity_alphatest"},
                "textures": bedrock_textures,
                "geometry": {"default": "geometry.luckycasino.card"},
                "animations": {
                    "deal": "animation.luckycasino.card.deal",
                    "flip_up": "animation.luckycasino.card.flip_up",
                    "flip_down": "animation.luckycasino.card.flip_down",
                    "pose_up": "animation.luckycasino.card.pose_up",
                    "ctrl": "controller.animation.luckycasino.card",
                },
                "scripts": {"animate": ["ctrl"]},
                "render_controllers": ["controller.render.luckycasino.card"],
            }
        },
    })
    write_json(BEDROCK / "render_controllers" / "card.render_controllers.json", {
        "format_version": "1.8.0",
        "render_controllers": {
            "controller.render.luckycasino.card": {
                "arrays": {"textures": {"Array.cards": [f"Texture.c{i}" for i in range(53)]}},
                "geometry": "Geometry.default",
                "materials": [{"*": "Material.default"}],
                "textures": ["Array.cards[q.property('luckycasino:card')]"],
            }
        },
    })


if __name__ == "__main__":
    main()
