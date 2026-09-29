import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIXED = ["X-Wing", "Y-Wing", "Swordfish", "XYZ-Wing"]
TITLE = "Bare Sudoku"
RTL = ("ar", "fa", "he")


def load():
    with open(os.path.join(ROOT, "i18n", "strings.json"), encoding="utf-8") as f:
        data = json.load(f)
    keys = data["keys"]
    languages = data["languages"]
    for code, values in languages.items():
        if len(values) != len(keys) + 1:
            raise SystemExit("%s: %d deger bekleniyor, %d var" % (code, len(keys) + 1, len(values)))
        for k, v in zip(keys, values[1:]):
            if not v:
                raise SystemExit("%s: %s bos" % (code, k))
            if k in ("naked", "row", "col", "box") and "#" not in v:
                raise SystemExit("%s: %s icinde # yok" % (code, k))
    return keys, languages


def table(keys, values):
    body = values[1:]
    split = keys.index("language")
    return body[:split] + FIXED + [TITLE] + body[split:]


def ordered(languages):
    return ["en"] + [c for c in languages if c != "en"]


def web(keys, languages):
    return {code: {"name": languages[code][0], "rtl": code in RTL, "s": table(keys, languages[code])} for code in ordered(languages)}


def java_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def write_java(keys, languages):
    lines = ["package com.baresudoku;", "", "final class Strings {", "    static final String[] TABLE = {"]
    for code in ordered(languages):
        entry = "\n".join([code, languages[code][0]] + table(keys, languages[code]))
        lines.append("        " + java_string(entry.replace("\n", "\\n")) + ",")
    lines += [
        "    };",
        "",
        "    static String[] forTag(String tag) {",
        "        String best = TABLE[0];",
        "        int bestScore = 0;",
        "        for (String entry : TABLE) {",
        "            int score = match(tag, entry.substring(0, entry.indexOf('\\n')));",
        "            if (score > bestScore) {",
        "                bestScore = score;",
        "                best = entry;",
        "            }",
        "        }",
        "        String[] parts = best.split(\"\\n\");",
        "        String[] out = new String[parts.length - 2];",
        "        System.arraycopy(parts, 2, out, 0, out.length);",
        "        return out;",
        "    }",
        "",
        "    static int match(String tag, String code) {",
        "        if (tag.equals(code)) return 3;",
        "        if (tag.startsWith(code + \"-\")) return 2;",
        "        String lang = language(tag);",
        "        if (lang.equals(\"zh\") && code.equals(\"zh-Hant\") && (tag.contains(\"TW\") || tag.contains(\"HK\") || tag.contains(\"MO\"))) return 2;",
        "        if (lang.equals(\"no\") || lang.equals(\"nn\")) lang = \"nb\";",
        "        if (lang.equals(\"iw\")) lang = \"he\";",
        "        if (lang.equals(\"in\")) lang = \"id\";",
        "        return lang.equals(language(code)) ? 1 : 0;",
        "    }",
        "",
        "    static String language(String tag) {",
        "        int dash = tag.indexOf('-');",
        "        return dash > 0 ? tag.substring(0, dash) : tag;",
        "    }",
        "}",
        "",
    ]
    with open(os.path.join(ROOT, "src", "com", "baresudoku", "Strings.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


if __name__ == "__main__":
    keys, languages = load()
    write_java(keys, languages)
    print("%d dil, %d metin" % (len(languages), len(keys)))
