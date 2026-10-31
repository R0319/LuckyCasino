#!/usr/bin/env python3
"""ルールブックの見本（dist/rulebook.html）を描く。

先に paper のテスト（./gradlew :paper:test）でページを paper/build/rulebook/*.json に書き出しておく。
Java 版の本と同じ大きさ（1 行 114 px、14 行、行の高さ 9 px）で並べ、折り返しはマインクラフトと同じく
空白で、空白がなければ文字の途中で切る。かな・漢字の幅は Unifont の絵の幅から出すので、実機とほぼ同じになる。
はみ出し（15 行以上）と、背の高い絵の下に文字があって重なる行は、最後に一覧で出す。

使い方: tools/preview で python3 rulebook.py
"""
import base64
import html
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
PAGES = ROOT / "paper" / "build" / "rulebook"
ASSETS = ROOT / "resourcepacks" / "java" / "assets" / "luckycasino"
OUT = ROOT / "dist" / "rulebook.html"
UNIFONT = "/usr/share/fonts/opentype/unifont/unifont_jp.otf"

S = 2                        # 描く倍率
LINE_W, LINES, LINE_H = 114, 14, 9
PAGE_W, PAGE_H = 146, 180
LEFT, TOP = 16, 22
COLORS = {"black": (0, 0, 0), "dark_blue": (0, 0, 170), "dark_gray": (85, 85, 85), "gray": (170, 170, 170),
          "white": (255, 255, 255), "dark_red": (170, 0, 0), "red": (255, 85, 85), "gold": (255, 170, 0)}
NARROW = {**{c: 2 for c in "i!.,:;|'"}, **{c: 3 for c in "l`"}, **{c: 4 for c in "It[](){}*\" "},
          **{c: 5 for c in "fk<>"}, **{c: 7 for c in "@~"}}

GAMES = [("blackjack", "ブラックジャック"), ("roulette", "ルーレット"), ("poker", "ポーカー"), ("slot", "スロット"),
         ("baccarat", "バカラ"), ("bigwheel", "ビッグホイール"), ("chinchiro", "チンチロ"), ("highlow", "ハイ＆ロー"),
         ("horserace", "競馬"), ("holdem", "カジノホールデム")]

font = ImageFont.truetype(UNIFONT, 16)
_unihex = {}


def unihex_advance(ch):
    """Unifont の絵の左右の端から、マインクラフトの文字送り（幅 / 2 + 1）を出す。"""
    if ch not in _unihex:
        img = Image.new("L", (32, 20))
        ImageDraw.Draw(img).text((0, 0), ch, font=font, fill=255)
        box = img.getbbox()
        width = 0 if box is None else box[2] - box[0]
        _unihex[ch] = (width // 2 + 1 if width else 4, box[0] if box else 0)
    return _unihex[ch]


def load_icons():
    icons = {}
    data = json.loads((ASSETS / "font" / "icons.json").read_text(encoding="utf-8"))
    for p in data["providers"]:
        sheet = Image.open(ASSETS / "textures" / p["file"].split(":")[1]).convert("RGBA")
        rows = p["chars"]
        cw, ch = sheet.width // len(rows[0]), sheet.height // len(rows)
        for r, line in enumerate(rows):
            for c, code in enumerate(line):
                glyph = sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
                scale = p["height"] / ch
                box = glyph.getbbox()
                right = box[2] if box else 0
                icons[code] = (glyph, p["ascent"], p["height"], round(right * scale) + 1, scale)
    return icons


ICONS = load_icons()


def flatten(node, style, out):
    """Adventure の JSON を（文字, 色, 太字, フォント）の並びにする。"""
    if isinstance(node, str):
        node = {"text": node}
    st = dict(style)
    for key in ("color", "bold", "font"):
        if key in node:
            st[key] = node[key]
    for ch in node.get("text", ""):
        out.append((ch, st))
    for child in node.get("extra", []):
        flatten(child, st, out)
    return out


def advance(ch, st):
    if st.get("font") == "luckycasino:icons":
        return ICONS[ch][3] if ch in ICONS else 8
    if ord(ch) < 128:
        w = NARROW.get(ch, 6)
    else:
        w = unihex_advance(ch)[0]
    return w + (1 if st.get("bold") and ch != " " else 0)


def wrap(chars):
    """改行と幅 114 px で行に分ける。"""
    lines, line, width, last_space = [], [], 0, -1
    for ch, st in chars:
        if ch == "\n":
            lines.append(line)
            line, width, last_space = [], 0, -1
            continue
        w = advance(ch, st)
        if width + w > LINE_W and line:
            if last_space >= 0:
                rest = line[last_space + 1:]
                lines.append(line[:last_space])
                line = rest
            else:
                lines.append(line)
                line = []
            width = sum(advance(c, s) for c, s in line)
            last_space = -1
        if ch == " ":
            last_space = len(line)
        line.append((ch, st))
        width += w
    lines.append(line)
    return lines


def parchment():
    img = Image.new("RGBA", (PAGE_W * S, PAGE_H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, PAGE_W * S - 1, PAGE_H * S - 1], radius=6, fill=(120, 84, 48, 255))
    d.rounded_rectangle([4, 4, PAGE_W * S - 5, PAGE_H * S - 5], radius=4, fill=(244, 234, 206, 255))
    for y in range(8, PAGE_H * S - 8, 6):
        d.line([(10, y), (PAGE_W * S - 11, y)], fill=(238, 226, 194, 255))
    return img


def draw_char(img, d, ch, st, x, baseline):
    color = COLORS.get(st.get("color", "black"), (0, 0, 0))
    if st.get("font") == "luckycasino:icons" and ch in ICONS:
        glyph, ascent, height, _, scale = ICONS[ch]
        g = glyph.resize((round(glyph.width * scale * S), round(height * S)), Image.NEAREST)
        img.alpha_composite(g, (x * S, (baseline - ascent) * S))
        return
    if ch == " ":
        return
    y = (baseline - 7) * S - 2
    if ord(ch) < 128:
        # 英数字は Unifont（幅 4）を横に 1.25 倍してマインクラフトの文字（幅 5）に近づける
        g = Image.new("RGBA", (8, 20), (0, 0, 0, 0))
        ImageDraw.Draw(g).text((0, 0), ch, font=font, fill=color + (255,))
        g = g.resize((10, 20), Image.NEAREST)
        for dx in ((0, S) if st.get("bold") else (0,)):
            img.alpha_composite(g, (x * S + dx, y))
        return
    off = unihex_advance(ch)[1]
    d.text((x * S - off, y), ch, font=font, fill=color)
    if st.get("bold"):
        d.text((x * S - off + S, y), ch, font=font, fill=color)


def render(page_json, number, total):
    chars = flatten(page_json, {"color": "black"}, [])
    lines = wrap(chars)
    img = parchment()
    d = ImageDraw.Draw(img)
    num = f"{number}/{total}"
    d.text(((LEFT + LINE_W) * S - len(num) * 8, 8 * S), num, font=font, fill=(0, 0, 0))
    problems = []
    tall_above = False
    for i, line in enumerate(lines):
        baseline = TOP + i * LINE_H + 7
        x = LEFT
        tall = False
        for ch, st in line:
            draw_char(img, d, ch, st, x, baseline)
            if st.get("font") == "luckycasino:icons" and ch in ICONS and ICONS[ch][2] - ICONS[ch][1] > 2:
                tall = True
            x += advance(ch, st)
        text = "".join(c for c, _ in line).strip()
        if tall_above and text:
            problems.append(f"{i + 1} 行目が上の絵と重なる")
        tall_above = tall
    if len(lines) > LINES:
        problems.append(f"{len(lines)} 行（{LINES} 行まで）")
        d.rectangle([0, (TOP + LINES * LINE_H) * S, PAGE_W * S, PAGE_H * S], outline=(220, 0, 0), width=3)
    return img, problems


def data_url(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def main():
    sections, all_problems = [], []
    for game, name in GAMES:
        for suffix, kind in (("", "Java 版（絵）"), ("-text", "統合版・パックなし（文字）"),
                             ("-en", "英語・Java 版（絵）"), ("-text-en", "英語・パックなし（文字）")):
            pages = json.loads((PAGES / f"{game}{suffix}.json").read_text(encoding="utf-8"))
            figs = []
            for n, page in enumerate(pages, 1):
                img, problems = render(page, n, len(pages))
                for p in problems:
                    all_problems.append(f"{name} {kind} {n} ページ: {p}")
                figs.append(f'<img src="{data_url(img)}" alt="{html.escape(name)} {n} ページ" width="{PAGE_W * S}">')
            sections.append((game, name, kind, figs))
    tabs = "".join(f'<button data-game="{g}">{html.escape(n)}</button>' for g, n in GAMES)
    body = "".join(
        f'<section data-game="{g}" data-kind="{("text" if "文字" in k else "pic") + ("-en" if "英語" in k else "")}"><h2>{html.escape(n)}'
        f'<small>{html.escape(k)}</small></h2><div class="pages">{"".join(f)}</div></section>'
        for g, n, k, f in sections)
    template = (Path(__file__).parent / "rulebook_template.html").read_text(encoding="utf-8")
    OUT.write_text(template.replace("<!--TABS-->", tabs).replace("<!--PAGES-->", body), encoding="utf-8")
    print(OUT, f"{OUT.stat().st_size // 1024} KB")
    for p in all_problems:
        print("要確認:", p)
    return 1 if all_problems else 0


if __name__ == "__main__":
    sys.exit(main())
