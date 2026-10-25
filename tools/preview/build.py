"""アニメーションのプレビュー（ブラウザで再生する 1 枚の HTML）を作る。

リソースパックのモデルとテクスチャを埋め込み、preview.js（プラグインと同じ位置・タイミングで
動きを再生する）と合わせて dist/preview.html に書き出す。

    cd tools/preview && python3 build.py
"""
import base64
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).parent
PACK = HERE.parent.parent / "resourcepacks" / "java" / "assets" / "luckycasino"
OUT = HERE.parent.parent / "dist" / "preview.html"


def data_uri(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def vanilla(name):
    """バニラのテクスチャは同梱できないので、色の近い代わりを描く。"""
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    if name == "block/dark_oak_planks":
        d.rectangle([0, 0, 15, 15], fill=(66, 43, 20, 255))
        for y in (3, 7, 11, 15):
            d.line([0, y, 15, y], fill=(48, 31, 14, 255))
        for x, y in ((5, 0), (12, 4), (3, 8), (9, 12)):
            d.line([x, y, x, y + 2], fill=(48, 31, 14, 255))
    elif name == "block/gold_block":
        d.rectangle([0, 0, 15, 15], fill=(246, 208, 61, 255))
        d.rectangle([0, 0, 15, 15], outline=(255, 236, 140, 255))
        d.line([15, 0, 15, 15], fill=(200, 160, 30, 255))
        d.line([0, 15, 15, 15], fill=(200, 160, 30, 255))
    elif name == "block/white_concrete":
        d.rectangle([0, 0, 15, 15], fill=(207, 213, 214, 255))
    else:
        d.rectangle([0, 0, 15, 15], fill=(255, 0, 255, 255))
    return img


def load_model(name, cache):
    """luckycasino:item/... を親までたどって、textures と elements をまとめる。"""
    if name in cache:
        return cache[name]
    ns, path = name.split(":") if ":" in name else ("minecraft", name)
    data = json.loads((PACK / "models" / (path + ".json")).read_text())
    textures, elements = {}, None
    if "parent" in data:
        parent = load_model(data["parent"], cache)
        textures.update(parent["textures"])
        elements = parent["elements"]
    textures.update(data.get("textures", {}))
    elements = data.get("elements", elements)
    cache[name] = {"textures": textures, "elements": elements}
    return cache[name]


def main():
    cache = {}
    models = {}
    for item in sorted((PACK / "items").rglob("*.json")):
        key = str(item.relative_to(PACK / "items"))[:-5]
        ref = json.loads(item.read_text())["model"]["model"]
        model = load_model(ref, cache)
        # テクスチャの参照（#front など）を実際の名前に
        def resolve(t):
            seen = 0
            while t.startswith("#") and seen < 8:
                t = model["textures"].get(t[1:], t)
                seen += 1
            return t
        elements = []
        for el in model["elements"]:
            faces = {f: {"uv": v.get("uv", [0, 0, 16, 16]), "texture": resolve(v["texture"])} for f, v in el["faces"].items()}
            elements.append({"from": el["from"], "to": el["to"], "faces": faces})
        models[key] = elements

    textures = {}
    for elements in models.values():
        for el in elements:
            for face in el["faces"].values():
                name = face["texture"]
                if name in textures:
                    continue
                ns, path = name.split(":") if ":" in name else ("minecraft", name)
                if ns == "luckycasino":
                    textures[name] = data_uri(Image.open(PACK / "textures" / (path + ".png")).convert("RGBA"))
                else:
                    textures[name] = data_uri(vanilla(path))

    assets = json.dumps({"models": models, "textures": textures}, separators=(",", ":"))
    html = (HERE / "template.html").read_text()
    html = html.replace("/*ASSETS*/", "const ASSETS = " + assets + ";")
    html = html.replace("/*PREVIEW_JS*/", (HERE / "preview.js").read_text())
    OUT.write_text(html)
    print(OUT, len(html) // 1024, "KB,", len(models), "models,", len(textures), "textures")


if __name__ == "__main__":
    main()
