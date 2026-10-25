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


# ---- ビッグホイール（paper の BigWheelLayout・common の BigWheel と合わせる） ----
BIGWHEEL_ORDER = ("J A B A E B A T A B A E A B W A B A A E T B A B A A B "
                  "L E A A B A B T E A A B A W B E A A B A T A B E A B A").split()
SEGMENT_COLOR = {
    "A": (232, 196, 60, 255), "B": (64, 120, 220, 255), "E": (158, 72, 200, 255), "T": (40, 160, 84, 255),
    "W": (232, 120, 40, 255), "J": (30, 30, 38, 255), "L": (196, 28, 58, 255),
}
SEGMENT_LABEL = {"A": "1", "B": "2", "E": "5", "T": "10", "W": "20", "J": "J", "L": "L"}
# 卓のマスの並び（paper の BigWheelTable.SPOTS と同じ順）
BW_SPOTS = ["A", "B", "E", "T", "W", "J", "L"]
BW_BOX_Z = 0.3


def bw_box_x(i):
    return -1.71 + 0.57 * i


def bigwheel_texture():
    """ホイールの正面。正面から見た向きで描く（マス 0 が上、時計回り）。数字は外側から縦に積む。"""
    import math
    size = 256
    img = gt.Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    step = 360 / 54
    R = size / 2
    dot = 2
    outer = R * 0.86
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            rp = math.hypot(dx, dy)
            r = rp / R
            if r > 1:
                continue
            if r > 0.93:
                img.putpixel((x, y), gt.WOOD)
                continue
            if r < 0.3:
                img.putpixel((x, y), GOLD if r < 0.22 else gt.WOOD_DARK)
                continue
            angle = math.degrees(math.atan2(dx, -dy)) % 360
            index = int((angle + step / 2) // step) % 54
            seg = BIGWHEEL_ORDER[index]
            color = SEGMENT_COLOR[seg]
            phase = (angle + step / 2) % step
            if phase < 0.6 or phase > step - 0.6:
                color = (230, 220, 200, 255)
            else:
                delta = math.radians(((angle - index * step + 180) % 360) - 180)
                t = rp * math.sin(delta)
                depth = outer - rp * math.cos(delta)
                label = SEGMENT_LABEL[seg]
                d = int(depth // (5 * dot + 2))
                within = depth - d * (5 * dot + 2)
                if depth >= 0 and d < len(label) and within < 5 * dot:
                    glyph = gt.GLYPHS[label[d]]
                    gw = len(glyph[0])
                    col = math.floor((t + gw * dot / 2) / dot)
                    row = int(within // dot)
                    if 0 <= col < gw and glyph[row][col] == "#":
                        color = WHITE if seg not in ("A",) else (40, 30, 20, 255)
            img.putpixel((x, y), color)
    # 外周の釘（マスの境目）
    d = ImageDraw.Draw(img)
    for i in range(54):
        a = math.radians(i * step + step / 2)
        px, py = c + R * 0.95 * math.sin(a), c - R * 0.95 * math.cos(a)
        d.ellipse([px - 1.5, py - 1.5, px + 1.5, py + 1.5], fill=GOLD)
    # 真ん中の「LC」（3 倍の字）
    logo = gt.Image.new("RGBA", (9, 7), (0, 0, 0, 0))
    gt.text(logo, "LC", 5, 1, gt.WOOD_DARK)
    logo = logo.resize((27, 21), gt.Image.NEAREST)
    img.alpha_composite(logo, (round(c - 13), round(c - 10)))
    return img


def bigwheel_model():
    tex = "#wheel"
    return {
        "textures": {"wheel": "luckycasino:block/bigwheel_wheel", "particle": "luckycasino:block/bigwheel_wheel"},
        "elements": [{
            "from": [0, 0, 7.75], "to": [16, 16, 8.25],
            "faces": {
                "north": {"texture": tex, "uv": [0, 0, 16, 16]},
                "south": {"texture": tex, "uv": [16, 0, 0, 16]},
            },
        }],
    }


def bigwheel_stand_model():
    """台と柱と、上の針。ItemDisplay は Y 軸まわりに 180 度回して描くので、z は前後を逆にしてある（z が大きいほど奥）。"""
    wood, gold = "#wood", "#gold"

    def el(frm, to, t):
        return {"from": frm, "to": to, "faces": {f: {"texture": t, "uv": [0, 0, 16, 16]}
                                                 for f in ("north", "south", "east", "west", "up", "down")}}
    return {
        "textures": {"wood": "minecraft:block/dark_oak_planks", "gold": "minecraft:block/gold_block",
                     "particle": "minecraft:block/dark_oak_planks"},
        "elements": [
            el([3, -15.2, 7], [13, -14.2, 13], wood),
            el([7, -14.2, 8.6], [9, 16, 10], wood),
            # 針（ホイールの手前・上）
            el([7, 29.4, 6.6], [9, 31.6, 7.6], gold),
            el([7.5, 28.2, 6.6], [8.5, 29.4, 7.6], gold),
        ],
    }


def bigwheel_felt():
    img = felt_base(192, 96)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 189, 93], outline=GOLD)
    text(img, "BIG WHEEL", 0, -0.8, GOLD)
    pays = {"A": "1:1", "B": "2:1", "E": "5:1", "T": "10:1", "W": "20:1", "J": "40:1", "L": "40:1"}
    names = {"A": "1", "B": "2", "E": "5", "T": "10", "W": "20", "J": "JOKER", "L": "LOGO"}
    for i, seg in enumerate(BW_SPOTS):
        x = bw_box_x(i)
        color = SEGMENT_COLOR[seg]
        box(d, x, BW_BOX_Z, 0.53, 0.62, GOLD, color)
        ink = (40, 30, 20, 255) if seg == "A" else WHITE
        text(img, names[seg], x, BW_BOX_Z - 0.12, ink)
        text(img, pays[seg], x, BW_BOX_Z + 0.14, ink)
    return img


def item(path):
    write_json(JAVA / "items" / f"{path}.json", {"model": {"type": "minecraft:model", "model": f"luckycasino:item/{path}"}})


def main():
    tex = JAVA / "textures" / "block"
    for_item_display(baccarat_felt(), 96).save(tex / "baccarat_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "baccarat.json", table_model("baccarat_felt"))
    item("table/baccarat")

    bigwheel_texture().save(tex / "bigwheel_wheel.png")
    for_item_display(bigwheel_felt(), 96).save(tex / "bigwheel_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "bigwheel.json", table_model("bigwheel_felt"))
    write_json(JAVA / "models" / "item" / "bigwheel" / "wheel.json", bigwheel_model())
    write_json(JAVA / "models" / "item" / "bigwheel" / "stand.json", bigwheel_stand_model())
    for path in ("table/bigwheel", "bigwheel/wheel", "bigwheel/stand"):
        item(path)


if __name__ == "__main__":
    main()
