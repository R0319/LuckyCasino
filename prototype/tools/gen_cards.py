#!/usr/bin/env python3
"""トランプ 52 枚 + 裏面のテクスチャとモデル定義を生成する。

使い方: python3 prototype/tools/gen_cards.py [フォントのパス]
出力先: prototype/resourcepacks/java と prototype/resourcepacks/bedrock
生成物はリポジトリにコミットするので、ビルド時に Python は不要。
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "resourcepacks" / "java" / "assets" / "luckycasino"
BEDROCK = ROOT / "resourcepacks" / "bedrock"

FONT_PATH = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# 並び順は common の CardCode と同じにすること（番号 = スート * 13 + ランク + 1、0 は裏向き）
SUITS = [("spade", "♠", (20, 20, 28)), ("heart", "♥", (196, 28, 36)),
         ("diamond", "♦", (196, 28, 36)), ("club", "♣", (20, 20, 28))]
RANKS = ["a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"]

W, H = 90, 126  # カード 1 面のピクセル数（縦横比 ≒ 63:88）


def font(size):
    return ImageFont.truetype(FONT_PATH, size)


def rounded_base(fill, border):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=7, fill=fill, outline=border, width=2)
    return img, d


def corner(rank, symbol, color):
    """左上の隅（ランク + 小さいスート）。右下は 180 度回して使う。"""
    tile = Image.new("RGBA", (24, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    label = rank.upper()
    f = font(15 if len(label) == 1 else 12)
    d.text((12, 10), label, font=f, fill=color, anchor="mm")
    d.text((12, 28), symbol, font=font(14), fill=color, anchor="mm")
    return tile


def front(suit_idx, rank_idx):
    _, symbol, color = SUITS[suit_idx]
    rank = RANKS[rank_idx]
    img, d = rounded_base((250, 248, 240, 255), (150, 150, 150, 255))
    c = corner(rank, symbol, color)
    img.alpha_composite(c, (3, 3))
    img.alpha_composite(c.rotate(180), (W - 27, H - 43))
    if rank in ("j", "q", "k"):
        d.rounded_rectangle([22, 28, W - 23, H - 29], radius=4, outline=color, width=2, fill=(244, 232, 200, 255))
        d.text((W // 2, H // 2 - 10), rank.upper(), font=font(34), fill=color, anchor="mm")
        d.text((W // 2, H // 2 + 20), symbol, font=font(22), fill=color, anchor="mm")
    elif rank == "a":
        d.text((W // 2, H // 2), symbol, font=font(52), fill=color, anchor="mm")
    else:
        d.text((W // 2, H // 2 - 4), symbol, font=font(40), fill=color, anchor="mm")
        d.text((W // 2, H // 2 + 30), rank.upper(), font=font(16), fill=color, anchor="mm")
    return img


def back():
    img, d = rounded_base((128, 18, 30, 255), (230, 190, 90, 255))
    d.rounded_rectangle([6, 6, W - 7, H - 7], radius=4, outline=(230, 190, 90, 255), width=1)
    gold = (214, 170, 70, 255)
    step = 12
    for y in range(10, H - 8, step):
        for x in range(10, W - 8, step):
            if (x // step + y // step) % 2 == 0:
                d.polygon([(x, y - 3), (x + 3, y), (x, y + 3), (x - 3, y)], fill=gold)
    d.ellipse([W // 2 - 20, H // 2 - 20, W // 2 + 20, H // 2 + 20], fill=(128, 18, 30, 255), outline=gold, width=2)
    d.text((W // 2, H // 2), "LC", font=font(18), fill=gold, anchor="mm")
    return img


def card_ids():
    for s, (suit, _, _) in enumerate(SUITS):
        for r, rank in enumerate(RANKS):
            yield s * 13 + r + 1, f"{suit}_{rank}", s, r


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def bedrock_sheet(front_img, back_img):
    # 256x128: 表 (0,0) / 裏 (128,0) / 側面用の白 (100,0)
    sheet = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    sheet.alpha_composite(front_img, (0, 0))
    sheet.alpha_composite(back_img, (128, 0))
    ImageDraw.Draw(sheet).rectangle([100, 0, 115, 15], fill=(236, 232, 222, 255))
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
