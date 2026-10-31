#!/usr/bin/env python3
"""新しいゲーム（バカラなど）のフェルト・モデルを生成する（Java 版）。

使い方: python3 tools/gen_games.py
座標の決まりは tools/gen_table.py と同じ（テーブル中心の床 = モデルの (8, 8, 8)、プレイヤー側が +Z、右が +X）。
フェルトは 1 ブロック = 48 px（192 x 96）。位置の数値は paper の各 Layout と合わせること。
"""
from PIL import ImageDraw

import gen_table as gt
from gen_table import GOLD, WHITE, FELT, FELT_LIGHT, JAVA, felt_base, for_item_display, table_model, write_json

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
    "=": ["...", "###", "...", "###", "..."],
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


# ---- ビッグホイールのステージ（paper の BigWheelLayout と合わせる） ----
STAGE_CENTER = (0.0, 2.1, -1.1)   # ステージのモデルを置く位置（ブロック、局所座標）
STAGE_SCALE = 2.0                 # 表示倍率（モデル 8 単位 = 1 ブロック）
BW_WHEEL = (0.0, 2.2, -1.55)
BW_WHEEL_D = 3.0
BW_SEAT_R, BW_STAND_R, BW_SPREAD = 3.0, 2.3, 1.2


def bw_angle(i):
    """席 i（0 が右端）の、ホイールの中心から見た角度（ラジアン、手前が 0）。"""
    return (0.5 - i / 4) * BW_SPREAD


def bw_stand(i):
    import math
    a = bw_angle(i)
    return (BW_STAND_R * math.sin(a), BW_WHEEL[2] + BW_STAND_R * math.cos(a))


def world_box(lo, hi, tex, center=STAGE_CENTER, scale=STAGE_SCALE, faces=("north", "south", "east", "west", "up", "down")):
    """局所座標（ブロック）の箱を、ItemDisplay の 180 度回転を打ち消したモデルの要素にする。1 ブロックごとに分けて模様を詰める。"""
    import math
    u = 16 / scale
    out = []

    def cuts(a, b):
        n = max(1, math.ceil(round(b - a, 6)))
        return [(a + (b - a) * k / n, a + (b - a) * (k + 1) / n) for k in range(n)]
    for x0, x1 in cuts(lo[0], hi[0]):
        for y0, y1 in cuts(lo[1], hi[1]):
            for z0, z1 in cuts(lo[2], hi[2]):
                fx = sorted((8 - (x0 - center[0]) * u, 8 - (x1 - center[0]) * u))
                fz = sorted((8 - (z0 - center[2]) * u, 8 - (z1 - center[2]) * u))
                fy = (8 + (y0 - center[1]) * u, 8 + (y1 - center[1]) * u)
                out.append({"from": [round(fx[0], 3), round(fy[0], 3), round(fz[0], 3)],
                            "to": [round(fx[1], 3), round(fy[1], 3), round(fz[1], 3)],
                            "faces": {f: {"texture": tex, "uv": [0, 0, 16, 16]} for f in faces}})
    return out


def bigwheel_stage_model():
    wood, wool, gold, green = "#wood", "#wool", "#gold", "#green"
    els = []
    els += world_box((-2, 0, -1.85), (2, 0.2, -0.3), wood)                  # 舞台の床
    els += world_box((-2, 0, -2), (2, 4, -1.85), wool)                     # 赤い幕
    els += world_box((-2.1, 4, -2.05), (2.1, 4.25, -1.8), gold)            # 上の金の縁
    for x in (-2.1, 1.9):
        els += world_box((x, 0, -2.05), (x + 0.2, 4, -1.75), wood)         # 両脇の柱
    wx, wy, wz = BW_WHEEL
    r = BW_WHEEL_D / 2
    els += world_box((-0.12, 0.2, -1.8), (0.12, wy, -1.62), wood)         # ホイールの軸の柱
    els += world_box((-0.12, wy + r + 0.08, wz + 0.02), (0.12, wy + r + 0.32, wz + 0.12), gold)  # 針
    els += world_box((-0.05, wy + r - 0.04, wz + 0.02), (0.05, wy + r + 0.08, wz + 0.12), gold)
    for i in range(5):
        x, z = bw_stand(i)
        els += world_box((x - 0.25, 0, z - 0.2), (x + 0.25, 0.95, z + 0.2), wood)   # ベット台
        els += world_box((x - 0.28, 0.95, z - 0.23), (x + 0.28, 1.0, z + 0.23), green)
    return {
        "textures": {"wood": "minecraft:block/dark_oak_planks", "wool": "minecraft:block/red_wool",
                     "gold": "minecraft:block/gold_block", "green": "minecraft:block/green_wool",
                     "particle": "minecraft:block/red_wool"},
        "elements": els,
    }


BW_BULBS = 24


def bw_bulb_points():
    import math
    wx, wy, wz = BW_WHEEL
    r = BW_WHEEL_D / 2 + 0.13
    return [(wx + r * math.sin(2 * math.pi * k / BW_BULBS), wy + r * math.cos(2 * math.pi * k / BW_BULBS), wz - 0.2)
            for k in range(BW_BULBS)]


def bigwheel_bulbs_model(phase):
    """ホイールのまわりの電球。phase 0 は偶数番、1 は奇数番が光る（2 つのモデルを交互に出して点滅させる）。"""
    els = []
    for k, (x, y, z) in enumerate(bw_bulb_points()):
        tex = "#on" if k % 2 == phase else "#off"
        els += world_box((x - 0.05, y - 0.05, z - 0.05), (x + 0.05, y + 0.05, z + 0.05), tex)
    return {"textures": {"on": "minecraft:block/glowstone", "off": "minecraft:block/redstone_lamp",
                         "particle": "minecraft:block/glowstone"}, "elements": els}


def bigwheel_icon_model():
    """手に持つ・メニューのアイコン（ホイールそのもの）。"""
    return {"parent": "luckycasino:item/bigwheel/wheel",
            "display": {"gui": {"rotation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
                        "ground": {"scale": [0.4, 0.4, 0.4]}, "fixed": {"scale": [0.8, 0.8, 0.8]},
                        "thirdperson_righthand": {"rotation": [0, 90, 0], "scale": [0.5, 0.5, 0.5]},
                        "firstperson_righthand": {"rotation": [0, 90, 0], "scale": [0.5, 0.5, 0.5]}}}


# ---- チンチロ（paper の ChinchiroLayout・ChinchiroTable と合わせる） ----
ZS_CENTER, ZS_SCALE = (0.0, 0.5, 0.0), 2.0   # 座敷のモデルを置く位置と倍率（ChinchiroLayout.modelAt・modelScale）
ZS_RING = 1.55                                # 座布団の輪の半径
ZS_MAT = 0.1                                  # 畳の表面の高さ


def zs_angle(i):
    """座布団 i の角度（度、手前が 0）。0〜4 が客（右から）、5 が親。"""
    return 180.0 if i == 5 else 120.0 - 60.0 * i


PIPS = {1: [(1, 1)], 2: [(0, 0), (2, 2)], 3: [(0, 0), (1, 1), (2, 2)], 4: [(0, 0), (2, 0), (0, 2), (2, 2)],
        5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], 6: [(0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)]}
DIE_WHITE = (244, 242, 236, 255)
PIP_BLACK = (28, 28, 34, 255)
PIP_RED = (200, 30, 40, 255)


def draw_die(d, x0, y0, size, face, pip):
    """size x size の目を (x0, y0) に描く（pip は目の 1 辺の px）。"""
    d.rectangle([x0, y0, x0 + size - 1, y0 + size - 1], fill=DIE_WHITE)
    color = PIP_RED if face in (1, 4) else PIP_BLACK
    for cx, cy in PIPS[face]:
        px = x0 + 1 + pip // 2 + round(cx * (size - 2 - 2 * pip) / 2)
        py = y0 + 1 + pip // 2 + round(cy * (size - 2 - 2 * pip) / 2)
        d.rectangle([px, py, px + pip - 1, py + pip - 1], fill=color)


def die_texture(face):
    img = gt.Image.new("RGBA", (16, 16), DIE_WHITE)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(210, 206, 196, 255))
    color = PIP_RED if face in (1, 4) else PIP_BLACK
    for cx, cy in PIPS[face]:
        x, y = 3 + cx * 4, 3 + cy * 4
        if face == 1:
            d.rectangle([6, 6, 9, 9], fill=color)
        else:
            d.rectangle([x, y, x + 1, y + 1], fill=color)
    return img


def die_model():
    t = lambda n: {"texture": f"#d{n}", "uv": [0, 0, 16, 16]}
    # ItemDisplay は Y 軸まわりに 180 度回して描くので、南に 2・北に 5・西に 3・東に 4 を置く
    # （回したあとの向き: 2 が北、5 が南、3 が東、4 が西。paper の ChinchiroTable.faceUp と合わせる）
    return {
        "textures": {**{f"d{n}": f"luckycasino:block/die_{n}" for n in range(1, 7)}, "particle": "luckycasino:block/die_1"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                      "faces": {"up": t(1), "down": t(6), "south": t(2), "north": t(5), "west": t(3), "east": t(4)}}],
    }


def tatami_texture():
    """畳 1 ブロック分。上下（z の端）に濃い緑の縁（へり）。長い畳は x 方向に並べてつなぐ。"""
    img = gt.Image.new("RGBA", (16, 16), (196, 190, 116, 255))
    for y in range(16):
        for x in range(16):
            if y % 2 == 0 and (x + y // 2) % 3 == 0:
                img.putpixel((x, y), (178, 172, 100, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 1], fill=(46, 74, 42, 255))
    d.rectangle([0, 14, 15, 15], fill=(46, 74, 42, 255))
    return img


def zs_box(lo, hi, tex, faces=("north", "south", "east", "west", "up", "down")):
    return world_box(lo, hi, tex, ZS_CENTER, ZS_SCALE, faces)


def chinchiro_zashiki_model():
    """4 x 4 の畳（2 x 1 の畳 8 枚）と、輪に並べた座布団 6 枚（5 枚は客、奥の赤い 1 枚は親）。"""
    import math
    els = zs_box((-2.05, 0, -2.05), (2.05, 0.04, 2.05), "#wood")
    for row in range(4):
        for col in range(2):
            x0, z0 = -2 + 2 * col, -2 + row
            els += zs_box((x0 + 0.01, 0.04, z0 + 0.01), (x0 + 1.99, ZS_MAT, z0 + 0.99), "#tatami", ("up", "north", "south", "east", "west"))
    for i in range(6):
        a = math.radians(zs_angle(i))
        x, z = ZS_RING * math.sin(a), ZS_RING * math.cos(a)
        els += zs_box((x - 0.31, ZS_MAT, z - 0.31), (x + 0.31, ZS_MAT + 0.11, z + 0.31), "#oya" if i == 5 else "#zabuton")
    return {
        "textures": {"wood": "minecraft:block/dark_oak_planks", "tatami": "luckycasino:block/tatami",
                     "zabuton": "minecraft:block/purple_wool", "oya": "minecraft:block/red_wool",
                     "particle": "luckycasino:block/tatami"},
        "elements": els,
    }


def chinchiro_bowl_model():
    """どんぶり（白に青い縁）。モデルの中心（8, 8, 8）が底の中心。16 角形の壁は要素の回転（22.5 度刻み）で作る。"""
    import math
    r, t, w = 6.0, 0.9, 2 * 6.0 * math.tan(math.radians(11.25)) + 0.3
    # 壁は低め（座った目の高さから中のサイコロが見えるように）
    bottom, wall, rim = 8.0, 8.0 + 1.6, 8.0 + 2.4
    els = []

    def box(frm, to, tex, angle=0.0):
        e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
             "faces": {f: {"texture": tex, "uv": [0, 0, 16, 16]} for f in ("north", "south", "east", "west", "up", "down")}}
        if angle:
            e["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": angle}
        return e
    # 底（四角を 0 度と 45 度で重ねて八角形に）
    for angle in (0.0, 45.0):
        els.append(box([8 - 4.2, bottom - 0.6, 8 - 4.2], [8 + 4.2, bottom + 0.6, 8 + 4.2], "#white", angle))
    for k in range(16):
        quarter, rest = divmod(k, 4)
        angle = (-45.0, -22.5, 0.0, 22.5)[rest]
        # quarter ごとに軸を入れ替えた位置に置き、残りの角度を要素の回転で足す
        dx, dz = [(0, 1), (1, 0), (0, -1), (-1, 0)][quarter]
        for lo, hi, tex in ((bottom, wall, "#white"), (wall, rim, "#blue")):
            if dx == 0:
                frm = [8 - w / 2, lo, 8 + dz * r - t / 2]
                to = [8 + w / 2, hi, 8 + dz * r + t / 2]
            else:
                frm = [8 + dx * r - t / 2, lo, 8 - w / 2]
                to = [8 + dx * r + t / 2, hi, 8 + w / 2]
            els.append(box(frm, to, tex, angle))
    return {"textures": {"white": "minecraft:block/white_concrete", "blue": "minecraft:block/blue_concrete",
                         "particle": "minecraft:block/white_concrete"}, "elements": els}


def chinchiro_icon_model():
    """手に持つ・メニューのアイコン（どんぶり）。"""
    return {"parent": "luckycasino:item/chinchiro/bowl",
            "display": {"gui": {"rotation": [30, 45, 0], "scale": [1.4, 1.4, 1.4]},
                        "ground": {"scale": [0.6, 0.6, 0.6]}, "fixed": {"scale": [1.2, 1.2, 1.2]},
                        "thirdperson_righthand": {"scale": [0.6, 0.6, 0.6]},
                        "firstperson_righthand": {"scale": [0.6, 0.6, 0.6]}}}


# ---- ハイ＆ロー（paper の HighLowLayout と合わせる） ----
HL_CARD_Z, HL_BASE_X, HL_NEXT_X, HL_BOX_Z, HL_HISTORY_Z = -0.4, -0.3, 0.3, 0.45, -0.8
GREEN = (60, 170, 80, 255)
FELT_DARK_LINE = (10, 50, 30, 255)


def arrow(d, cx, cy, up, color):
    """縦 9 px の矢印（up なら上向き）。"""
    s = -1 if up else 1
    d.polygon([(cx, cy + s * 4), (cx - 4, cy), (cx + 4, cy)], fill=color)
    d.rectangle([cx - 1, cy - (0 if up else 4), cx + 1, cy + (4 if up else 0)], fill=color)


def highlow_felt():
    img = felt_base(192, 96)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 189, 93], outline=GOLD)
    # カード置き場
    for x, name in ((HL_BASE_X, "BASE"), (HL_NEXT_X, "NEXT")):
        d.rectangle([u(x - 0.13), v(HL_CARD_Z - 0.18), u(x + 0.13), v(HL_CARD_Z + 0.18)], outline=GOLD)
        text(img, name, x, HL_CARD_Z + 0.26, GOLD)
    text(img, "HIGH-LOW", -1.0, -0.42, GOLD)
    text(img, "A=1 K=13", 1.2, -0.42, WHITE)
    # 履歴
    d.rectangle([u(-1.74), v(HL_HISTORY_Z - 0.17), u(-0.3), v(HL_HISTORY_Z + 0.17)], outline=tint(GOLD, 0.6), fill=tint(FELT_DARK_LINE, 0.5))
    # 枠
    for x, w, name, color in ((-1.15, 1.25, "HIGH", GREEN), (0, 0.8, "SAME", GOLD), (1.15, 1.25, "LOW", BLUE)):
        box(d, x, HL_BOX_Z, w, 0.62, GOLD, tint(color, 0.3))
        text(img, name, x, HL_BOX_Z + 0.04, WHITE)
        if name == "HIGH":
            arrow(d, u(x - 0.4), v(HL_BOX_Z + 0.04) + 1, True, WHITE)
            arrow(d, u(x + 0.4), v(HL_BOX_Z + 0.04) + 1, True, WHITE)
        elif name == "LOW":
            arrow(d, u(x - 0.4), v(HL_BOX_Z + 0.04) + 1, False, WHITE)
            arrow(d, u(x + 0.4), v(HL_BOX_Z + 0.04) + 1, False, WHITE)
        else:
            text(img, "=", x, HL_BOX_Z + 0.18, WHITE)
    return img


# ---- 競馬（paper の HorseRaceLayout と合わせる） ----
# 枠の色（JRA の枠色: 1 白、2 黒、3 赤、4 青、5 黄、6 緑）
SILKS = [(240, 240, 236, 255), (36, 36, 40, 255), (210, 40, 40, 255), (40, 90, 210, 255), (240, 210, 40, 255), (40, 160, 70, 255)]
HR_LANE_Z = [-1.75 + 0.3 * i for i in range(6)]
HR_START_X, HR_FINISH_X, HR_GATE_X = -4.1, 4.2, -3.8
HR_CENTER, HR_SCALE = (0.0, 1.0, 0.0), 3.5   # コースのモデルを置く位置と倍率（HorseRaceLayout.modelAt・COURSE_SCALE）
HR_UPPER = 0.7                               # 観客席の上の段の床
HR_SEAT_X = (-2.4, 0.0, 2.4)
HR_ROWS = ((0.0, 0.9), (HR_UPPER, 1.8))      # （床の高さ, 席の z）
DIRT = (150, 110, 70, 255)
DIRT_DARK = (128, 92, 58, 255)


def horse_coat():
    img = gt.Image.new("RGBA", (16, 16), (120, 72, 40, 255))
    for y in range(16):
        for x in range(16):
            if (x * 5 + y * 3) % 7 == 0:
                img.putpixel((x, y), (104, 62, 34, 255))
    return img


def silk_texture(i):
    img = gt.Image.new("RGBA", (16, 16), SILKS[i])
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=tuple(max(0, c - 40) for c in SILKS[i][:3]) + (255,))
    return img


def horse_model(i):
    """+X（ゴール）へ走る馬と騎手。ItemDisplay は Y 軸まわりに 180 度回して描くので、モデルでは頭を -X に置く。"""
    coat, mane, silk, white = "#coat", "#mane", "#silk", "#white"

    def el(frm, to, t):
        return {"from": frm, "to": to, "faces": {f: {"texture": t, "uv": [0, 0, 16, 16]}
                                                 for f in ("north", "south", "east", "west", "up", "down")}}
    elements = [
        el([4, 6, 6.5], [12, 10, 9.5], coat),          # 胴
        el([2, 9, 7], [5, 13, 9], coat),               # 首
        el([0, 12, 7], [3.5, 14.5, 9], coat),          # 頭
        el([4.5, 12, 7.5], [5.5, 13.5, 8.5], mane),    # たてがみ
        el([12, 6.5, 7.5], [13.5, 10, 8.5], mane),     # しっぽ
        el([6, 10, 6], [10, 10.6, 10], white),         # 鞍の下の布（ゼッケン）
        el([7, 10.6, 7], [9.5, 14, 9], silk),          # 騎手の胴（勝負服）
        el([6.6, 14, 7.2], [9, 15.6, 8.8], silk),      # 帽子
    ]
    for lx in (4.5, 10.5):
        for lz in (6.5, 8.5):
            elements.append(el([lx, 0, lz], [lx + 1, 6, lz + 1], coat))
    return {
        "textures": {"coat": "luckycasino:block/horse_coat", "mane": "luckycasino:block/horse_mane",
                     "silk": f"luckycasino:block/silk_{i + 1}", "white": "luckycasino:block/silk_1",
                     "particle": "luckycasino:block/horse_coat"},
        "elements": elements,
    }


def dirt_texture():
    img = gt.Image.new("RGBA", (16, 16), DIRT)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 7) % 9 == 0:
                img.putpixel((x, y), DIRT_DARK)
            elif (x * 5 + y * 11) % 13 == 0:
                img.putpixel((x, y), (166, 126, 84, 255))
    return img


def hr_box(lo, hi, tex, faces=("north", "south", "east", "west", "up", "down")):
    return world_box(lo, hi, tex, HR_CENTER, HR_SCALE, faces)


def horserace_course_model():
    """直線のダートコース・ゲート・ゴール板・オッズ板・2 段の観客席・ベット台・払い戻し所。"""
    dirt, white, iron, green, black, gold, stone, wood, red = (
        "#dirt", "#white", "#iron", "#green", "#black", "#gold", "#stone", "#wood", "#red")
    els = []
    els += hr_box((-4.95, 0, -1.95), (4.95, 0.06, -0.05), dirt, ("up", "north", "south", "east", "west"))
    for z in (-1.98, -0.02):                                            # 内と外の白い柵
        els += hr_box((-4.95, 0.3, z - 0.03), (4.95, 0.36, z + 0.03), white)
        for k in range(11):
            x = -4.9 + k * 0.98
            els += hr_box((x - 0.03, 0, z - 0.03), (x + 0.03, 0.3, z + 0.03), white)
    # ゴールの線と柱・板
    els += hr_box((HR_FINISH_X - 0.03, 0.06, -1.95), (HR_FINISH_X + 0.03, 0.065, -0.05), white, ("up",))
    els += hr_box((HR_FINISH_X - 0.04, 0, 0.03), (HR_FINISH_X + 0.04, 1.5, 0.11), white)
    els += hr_box((HR_FINISH_X - 0.3, 1.15, 0.05), (HR_FINISH_X + 0.3, 1.5, 0.09), red)
    # スタートゲート（レーンの仕切り・屋根・後ろの枠。扉は horserace/gate で別に動かす）
    gx = HR_GATE_X
    els += hr_box((gx - 0.55, 0, -1.97), (gx - 0.45, 1.0, -0.03), iron)        # 後ろの枠
    els += hr_box((gx - 0.6, 0.9, -2.0), (gx + 0.05, 1.02, 0.0), green)        # 屋根
    for k in range(7):
        z = HR_LANE_Z[0] - 0.15 + k * 0.3
        els += hr_box((gx - 0.5, 0.06, z - 0.015), (gx, 0.9, z + 0.015), iron)  # 仕切り
    # オッズ板（コースの奥）。モデルは z が逆なので、手前（観客席側）を向く面は "north"
    els += hr_box((-1.95, 0.45, -2.0), (1.95, 2.45, -1.96), gold)
    els += hr_box((-1.85, 0.53, -1.97), (1.85, 2.37, -1.95), black, ("north",))
    for x in (-1.8, 1.8):
        els += hr_box((x - 0.05, 0, -2.0), (x + 0.05, 0.45, -1.96), iron)
    # 観客席（下の段は床、上の段は台の上）とベット台
    els += hr_box((-3.9, 0, 1.35), (3.9, HR_UPPER, 2.25), stone)
    for floor, z in HR_ROWS:
        for x in HR_SEAT_X:
            sz = z - 0.6
            els += hr_box((x - 0.3, floor, sz - 0.18), (x + 0.3, floor + 0.9, sz + 0.18), wood)
            els += hr_box((x - 0.33, floor + 0.9, sz - 0.21), (x + 0.33, floor + 0.95, sz + 0.21), green)
    # 払い戻し所
    els += hr_box((4.1, 0, 0.25), (4.7, 0.9, 0.55), wood)
    els += hr_box((4.08, 0.9, 0.23), (4.72, 0.95, 0.57), green)
    return {
        "textures": {"dirt": "luckycasino:block/horserace_dirt", "white": "minecraft:block/white_concrete",
                     "iron": "minecraft:block/iron_block", "green": "minecraft:block/green_wool",
                     "black": "minecraft:block/black_concrete", "gold": "minecraft:block/gold_block",
                     "stone": "minecraft:block/smooth_stone", "wood": "minecraft:block/dark_oak_planks",
                     "red": "minecraft:block/red_concrete", "particle": "luckycasino:block/horserace_dirt"},
        "elements": els,
    }


def horserace_gate_model():
    """ゲートの扉 1 枚。蝶番（手前の端）をモデルの原点に置き、そこから奥（-Z）へ 0.28 ブロック。"""
    els = world_box((-0.02, 0.0, -0.28), (0.02, 0.6, 0.0), "#green", (0, 0, 0), 1.0)
    return {"textures": {"green": "minecraft:block/green_wool", "particle": "minecraft:block/green_wool"}, "elements": els}


def horserace_icon_model():
    """手に持つ・メニューのアイコン（1 番の馬）。"""
    return {"parent": "luckycasino:item/horserace/horse_1",
            "display": {"gui": {"rotation": [0, 90, 0], "scale": [1.0, 1.0, 1.0]},
                        "ground": {"scale": [0.5, 0.5, 0.5]}, "fixed": {"rotation": [0, 90, 0], "scale": [1.0, 1.0, 1.0]},
                        "thirdperson_righthand": {"rotation": [0, 90, 0], "scale": [0.5, 0.5, 0.5]},
                        "firstperson_righthand": {"rotation": [0, 90, 0], "scale": [0.5, 0.5, 0.5]}}}


# ---- カジノホールデム（paper の HoldemLayout と合わせる） ----
HD_SEATS = [1.6 - 0.8 * i for i in range(5)]
HD_HOLE_Z, HD_BET_Z, HD_BET_DX, HD_BOARD_Z, HD_DEALER_Z = 0.62, 0.22, 0.17, -0.22, -0.66
HD_CARD_STEP, HD_BOARD_STEP = 0.21, 0.27


def card_outline(d, x, z, color):
    d.rectangle([u(x - 0.12), v(z - 0.18), u(x + 0.12), v(z + 0.18)], outline=color)


def holdem_felt():
    img = felt_base(192, 96)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 189, 93], outline=GOLD)
    text(img, "CASINO HOLDEM", -1.3, HD_BOARD_Z, GOLD)
    text(img, "DEALER 4-4 UP", -1.3, HD_BOARD_Z + 0.15, WHITE)
    text(img, "DEALER", -0.5, HD_DEALER_Z, GOLD)
    for k in range(2):
        card_outline(d, (k - 0.5) * HD_CARD_STEP, HD_DEALER_Z, FELT_LIGHT)
    for i in range(5):
        card_outline(d, (i - 2) * HD_BOARD_STEP, HD_BOARD_Z, FELT_LIGHT)
    for x in HD_SEATS:
        d.rectangle([u(x - 0.24), v(HD_HOLE_Z - 0.18), u(x + 0.24), v(HD_HOLE_Z + 0.18)], outline=WHITE)
        for dx, color in ((-HD_BET_DX, WHITE), (HD_BET_DX, GOLD)):
            cx, cy, r = u(x + dx), v(HD_BET_Z), 5
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, fill=tint(color, 0.15))
    # 丸の色の見本（白がアンティ、金がコール）
    for z, label, color in ((HD_BOARD_Z - 0.1, "ANTE", WHITE), (HD_BOARD_Z + 0.1, "CALL", GOLD)):
        cx, cy, r = u(1.0), v(z), 3
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, fill=tint(color, 0.15))
        text(img, label, 1.31, z, color)
    return img


def item(path):
    write_json(JAVA / "items" / f"{path}.json", {"model": {"type": "minecraft:model", "model": f"luckycasino:item/{path}"}})


def main():
    tex = JAVA / "textures" / "block"
    for_item_display(baccarat_felt(), 96).save(tex / "baccarat_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "baccarat.json", table_model("baccarat_felt"))
    item("table/baccarat")

    bigwheel_texture().save(tex / "bigwheel_wheel.png")
    write_json(JAVA / "models" / "item" / "table" / "bigwheel.json", bigwheel_icon_model())
    write_json(JAVA / "models" / "item" / "bigwheel" / "wheel.json", bigwheel_model())
    write_json(JAVA / "models" / "item" / "bigwheel" / "stage.json", bigwheel_stage_model())
    for phase in (0, 1):
        write_json(JAVA / "models" / "item" / "bigwheel" / f"bulbs_{phase}.json", bigwheel_bulbs_model(phase))
    for path in ("table/bigwheel", "bigwheel/wheel", "bigwheel/stage", "bigwheel/bulbs_0", "bigwheel/bulbs_1"):
        item(path)

    for n in range(1, 7):
        die_texture(n).save(tex / f"die_{n}.png")
    tatami_texture().save(tex / "tatami.png")
    write_json(JAVA / "models" / "item" / "table" / "chinchiro.json", chinchiro_icon_model())
    write_json(JAVA / "models" / "item" / "chinchiro" / "die.json", die_model())
    write_json(JAVA / "models" / "item" / "chinchiro" / "zashiki.json", chinchiro_zashiki_model())
    write_json(JAVA / "models" / "item" / "chinchiro" / "bowl.json", chinchiro_bowl_model())
    for path in ("table/chinchiro", "chinchiro/die", "chinchiro/zashiki", "chinchiro/bowl"):
        item(path)

    for_item_display(highlow_felt(), 96).save(tex / "highlow_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "highlow.json", table_model("highlow_felt"))
    item("table/highlow")

    for_item_display(holdem_felt(), 96).save(tex / "holdem_felt.png")
    write_json(JAVA / "models" / "item" / "table" / "holdem.json", table_model("holdem_felt"))
    item("table/holdem")

    horse_coat().save(tex / "horse_coat.png")
    gt.Image.new("RGBA", (16, 16), (50, 30, 20, 255)).save(tex / "horse_mane.png")
    for i in range(6):
        silk_texture(i).save(tex / f"silk_{i + 1}.png")
        write_json(JAVA / "models" / "item" / "horserace" / f"horse_{i + 1}.json", horse_model(i))
        item(f"horserace/horse_{i + 1}")
    dirt_texture().save(tex / "horserace_dirt.png")
    write_json(JAVA / "models" / "item" / "horserace" / "course.json", horserace_course_model())
    write_json(JAVA / "models" / "item" / "horserace" / "gate.json", horserace_gate_model())
    write_json(JAVA / "models" / "item" / "table" / "horserace.json", horserace_icon_model())
    for path in ("horserace/course", "horserace/gate", "table/horserace"):
        item(path)


if __name__ == "__main__":
    main()
