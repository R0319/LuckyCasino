#!/usr/bin/env python3
"""翻訳のキー（コードに書いた日本語の文）を集め、言語ファイルを作り直す。

キーはコードの文字列そのもの（"..." + "..." のつなぎは 1 つにまとめ、先頭の空白は除く。L10n.tr と同じ）。
- resourcepacks/java/assets/luckycasino/lang/en_us.json: 英訳。新しいキーは tools/i18n/en_us.json に足す
- ja_jp.json: キーと同じ文（クライアントは足りないキーを英語で出すため、全部入れる）

使い方: python3 tools/i18n_keys.py            言語ファイルを作り直す（英訳の無いキーを一覧で出す）
        python3 tools/i18n_keys.py --check    英訳の無いキー・使っていない英訳があれば 1 で終わる
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / "paper" / "src" / "main" / "java", ROOT / "common" / "src" / "main" / "java"]
TRANSLATIONS = ROOT / "tools" / "i18n" / "en_us.json"
LANG = ROOT / "resourcepacks" / "java" / "assets" / "luckycasino" / "lang"
JAPANESE = re.compile(r"[぀-ヿ一-鿿！-～]")
STRING = r'"(?:[^"\\\n]|\\.)*"'
# 日本語のまま使う、表示しない文字列（コード中の判定用など）
IGNORE = {"の目", "のゾロ目", "チェリー ", "つ", "が 3 つ"}


def strip_comments(src):
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            i = src.find("\n", i)
            i = n if i < 0 else i
        elif src.startswith("/*", i):
            i = src.find("*/", i) + 2
        elif src[i] == '"':
            m = re.compile(STRING).match(src, i)
            out.append(m.group())
            i = m.end()
        elif src[i] == "'":
            end = src.find("'", i + (3 if src[i + 1] == "\\" else 2))
            out.append(src[i:end + 1])
            i = end + 1
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


def unescape(literal):
    return json.loads(literal)


def keys():
    found = set()
    joined = re.compile(STRING + r"(?:\s*\+\s*" + STRING + r")*")
    for root in SOURCES:
        for path in sorted(root.rglob("*.java")):
            src = strip_comments(path.read_text(encoding="utf-8"))
            for m in joined.finditer(src):
                text = "".join(unescape(s) for s in re.findall(STRING, m.group())).lstrip(" ")
                if JAPANESE.search(text) and text not in IGNORE:
                    found.add(text)
    return found


def main():
    check = "--check" in sys.argv
    wanted = keys()
    english = json.loads(TRANSLATIONS.read_text(encoding="utf-8")) if TRANSLATIONS.exists() else {}
    missing = sorted(k for k in wanted if k not in english)
    unused = sorted(k for k in english if k not in wanted)
    if check:
        for k in missing:
            print("英訳がありません:", k)
        for k in unused:
            print("使っていない英訳:", k)
        return 1 if missing or unused else 0
    ordered = sorted(wanted)
    LANG.mkdir(parents=True, exist_ok=True)
    (LANG / "en_us.json").write_text(json.dumps({k: english[k] for k in ordered if k in english}, ensure_ascii=False, indent=1) + "\n",
                                    encoding="utf-8")
    (LANG / "ja_jp.json").write_text(json.dumps({k: k for k in ordered}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"キー {len(ordered)} 個、英訳なし {len(missing)} 個、使っていない英訳 {len(unused)} 個")
    return 0


if __name__ == "__main__":
    sys.exit(main())
