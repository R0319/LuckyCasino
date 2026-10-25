#!/usr/bin/env python3
"""新しいゲーム（バカラなど）のフェルト・モデルを生成する（Java 版）。

使い方: python3 tools/gen_games.py
座標の決まりは tools/gen_table.py と同じ（テーブル中心の床 = モデルの (8, 8, 8)、プレイヤー側が +Z、右が +X）。
フェルトは 1 ブロック = 48 px（192 x 96）。位置の数値は paper の各 Layout と合わせること。
"""
from PIL import ImageDraw

import gen_table as gt
from gen_table import GOLD, WHITE, FELT, JAVA, felt_base, for_item_display, table_model, write_json

# 3x5 の字（gen_table にない分）
gt.GLYPHS.update({
    "I": ["###", ".#.", ".#.", ".#.", "###"],
    "G": [".##", "#..", "#.#", "#.#", ".##"],
    "W": ["#.#", "#.#", "###", "###", "#.#"],
    "F": ["###", "#..", "##.", "#..", "#.."],
    "Z": ["###", "..#", ".#.", "#..", "###"],
    "Q": [".#.", "#.#", "#.#", ".#.", "..#"],
    "!": ["#", "#", "#", ".", "#"],
    "x": ["...", "#.#", ".#.", "#.#", "..."],
    # gen_table の N は M と同じ形なので、4 列で描き直す
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
})

PX = 48
BLUE = (64, 120, 210, 255)
RED = (200, 52, 52, 255)
GREEN_LINE = (120, 220, 140, 255)


def u(x):
    return round((x + 2) * PX)


def v(z):
    return round((z + 1) * PX)


def text(img, s, x, z, color):
    """局所座標 (x, z) を中心に字を書く。"""
    gt.text(img, s, u(x), v(z) - 2, color)


def box(d, x, z, w, h, outline, fill=None):
    d.rectangle([u(x - w / 2), v(z - h / 2), u(x + w / 2), v(z + h / 2)], outline=outline, fill=fill)


def tint(color, k=0.25):
    return tuple(round(FELT[i] * (1 - k) + color[i] * k) for i in range(3)) + (255,)


# ---- バカラ（paper の BaccaratLayout と合わせる） ----
SEATS = [1.6 - 0.8 * i for i in range(5)]
PLAYER_Z, BANKER_Z, TIE_Z, PAIR_DX = 0.62, 0.30, 0.03, 0.25
CARD_Z, PLAYER_CARD_X, BANKER_CARD_X, CARD_STEP = -0.48, -0.75, 0.35, 0.2


def baccarat_felt():
    img = felt_base(192, 96)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 189, 93], outline=GOLD)
    text(img, "BACCARAT", 0, -0.88, GOLD)
    # カード置き場（3 枚ぶん）
    for x0, color, name in ((PLAYER_CARD_X, BLUE, "PLAYER"), (BANKER_CARD_X, RED, "BANKER")):
        left, right = x0 - 0.14, x0 + CARD_STEP * 2 + 0.14
        d.rectangle([u(left), v(CARD_Z - 0.2), u(right), v(CARD_Z + 0.2)], outline=color)
        text(img, name, (left + right) / 2, CARD_Z + 0.29, color)
    # 席ごとの賭けの枠
    for x in SEATS:
        box(d, x, PLAYER_Z, 0.66, 0.26, BLUE, tint(BLUE))
        text(img, "PLAYER", x, PLAYER_Z, WHITE)
        box(d, x, BANKER_Z, 0.66, 0.26, RED, tint(RED))
        text(img, "BANKER", x, BANKER_Z, WHITE)
        box(d, x, TIE_Z, 0.28, 0.2, GREEN_LINE, tint(GREEN_LINE, 0.15))
        text(img, "TIE", x, TIE_Z, WHITE)
        for dx, color, label in ((-PAIR_DX, BLUE, "P"), (PAIR_DX, RED, "B")):
            cx, cy, r = u(x + dx), v(TIE_Z), 5
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, fill=tint(color))
            gt.plot(img, gt.GLYPHS[label], cx - 1, cy - 2, WHITE)
    return img


def item(path):
    write_json(JAVA / "items" / f"{path}.json", {"model": {"type": "minecraft:model", "model": f"luckycasino:item/{path}"}})


def main():
    tex = JAVA / "textures" / "block"
    for_item_display(baccarat_felt(), 96).save(tex / "baccarat_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "baccarat.json", table_model("baccarat_felt"))
    item("table/baccarat")


if __name__ == "__main__":
    main()
